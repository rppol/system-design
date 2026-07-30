# Technology knowledge bank — taxonomy

The vocabulary every record in this directory is written against: 6 kinds, 8
language tokens, 18 tiers and the 95 roles beneath them. This file and its sibling
shards ARE the source of truth — `game/tech_index.json` is generated from them by
`game/extract.py` and is gitignored, exactly like the question banks.

This directory is DATA, not study content. Both of extract.py's walks skip it by
exact path (`TECH_BANK_DIR`), so nothing here is a module, contributes a question,
or may carry a `## NN.` section-template heading.

## Record contract

A record is a `###` heading (the tool's exact display name, matched against the
derived index), a contiguous block of `**Field:**` lines, then optional prose.

- `**Short:**` — REQUIRED, 15–220 chars, one self-contained sentence. This is the
  always-visible row line, and the same bound the Q&A `**Short:**` contract uses.
- `**Kind:**` — one kind id below. `**Lang:**` — comma-separated language tokens.
- `**Roles:**` — comma-separated `tier/role @weight`, weight 1|2|3, best first.
- The paragraphs after the blank line are the DESCRIPTION, revealed when the row is
  expanded. Optional and per-record opt-in; a description with no short line is fatal.
  No fences, lists, tables, or links — descriptions are escaped, not rendered.
- Nothing but records after the preamble: a footer would be read as the last
  record's description.

## Kinds

### tech
**Label:** Technology
**Default:** yes
**Def:** You install it, run it, import it, or subscribe to it. Products, libraries, servers, CLIs, managed services.
**Examples:** Redis, vLLM, Terraform, spaCy, HikariCP, Grafana Mimir, Redpanda

### api
**Label:** API symbol
**Def:** A named symbol inside a language, framework, or library: annotation, class, method, flag, env var, config key, endpoint. Belongs to a product; is not itself one.
**Examples:** @ConfigurationProperties, cudaMallocPitch, java.util.TreeMap, CUDA_LAUNCH_BLOCKING=1, /actuator/beans, torch.optim

### spec
**Label:** Protocol / format
**Def:** A protocol, wire format, file format, or published standard. Has implementations but is not one.
**Examples:** OTLP, OAuth 2.1, GGUF, ONNX, JSONPath, Conventional Commits, OpenAPI

### model
**Label:** Model
**Def:** A trained model, model family, or hosted model endpoint you call or download.
**Examples:** OpenAI GPT-5.6, CLIP, DeepSeek-Coder, Gemini Embedding API, Llama Guard, Whisper

### dataset
**Label:** Benchmark / dataset
**Def:** An evaluation benchmark or a corpus. Not the harness that runs it (that is tech).
**Examples:** MMLU, GSM8K, HumanEval, BrowseComp, OpenHermes 2.5, MMMU

### concept
**Label:** Concept / pattern
**Def:** A pattern, algorithm, principle, or technique with no shipped artifact.
**Examples:** Paxos, Abstract Factory, CRITIC, Distillation, Law of Demeter, two-phase commit

## Languages

### *
**Label:** Any language

### python
**Label:** Python

### java
**Label:** Java

### js
**Label:** JavaScript

### go
**Label:** Go

### cpp
**Label:** C++

### csharp
**Label:** C#

### rust
**Label:** Rust

## Tiers

### data-stores
**Label:** Databases
**Blurb:** Where records live. Pick by data shape and access pattern.

#### relational
**Label:** Relational & distributed SQL
**Def:** Row-store SQL engines, including NewSQL/global-ACID.
**Seeds:** PostgreSQL, MySQL, CockroachDB, TiDB, Citus, Aurora, FoundationDB

#### document
**Label:** Document
**Def:** JSON/BSON document stores.
**Seeds:** MongoDB, Couchbase, Firestore, DocumentDB

#### key-value-and-embedded
**Label:** Key-value & embedded
**Def:** KV stores as systems of record, plus embedded/in-process engines and the storage engines underneath other databases.
**Seeds:** DynamoDB, Valkey, etcd, RocksDB, SQLite, DuckDB, LMDB, InnoDB
**Merged:** 'embedded' + 'storage engine' folded in; each was 7-8 tools alone.

#### wide-column
**Label:** Wide-column
**Def:** Partition-key/clustering-column stores.
**Seeds:** Cassandra, ScyllaDB, HBase, Bigtable, nodetool

#### time-series
**Label:** Time-series
**Def:** Append-heavy, timestamp-indexed stores.
**Seeds:** Prometheus, InfluxDB, TimescaleDB, VictoriaMetrics, Thanos, QuestDB

#### vector-store
**Label:** Vector database
**Def:** A managed or self-hosted service whose primary index is a vector index. The LIBRARY form lives in search-and-retrieval/ann-index-library.
**Seeds:** Pinecone, Weaviate, Milvus, Qdrant, Chroma, pgvector, LanceDB, Vespa

#### graph-db
**Label:** Graph
**Def:** Property-graph and triple stores plus their query languages.
**Seeds:** Neo4j, Neptune, JanusGraph, ArangoDB, Cypher, Gremlin

#### object-and-file-storage
**Label:** Object & file storage
**Def:** Blob, file, and block storage, including cluster-attached volumes.
**Seeds:** S3, MinIO, GCS, Ceph, HDFS, EBS, CSI, local-path-provisioner

#### warehouse-and-olap
**Label:** Warehouse & OLAP
**Def:** Columnar analytics engines, lakehouse table formats, and cloud warehouses.
**Seeds:** ClickHouse, Snowflake, BigQuery, Druid, Iceberg, Delta Lake, Parquet, Redshift

### data-access
**Label:** Data access & DB ops
**Blurb:** Getting at the data and keeping it alive: mapping, pooling, migrating, replicating.

#### orm-and-data-mapping
**Label:** ORM & data mapping
**Def:** Object/relational mappers, repositories, query builders, template APIs.
**Seeds:** Hibernate, JPA, SQLAlchemy, MyBatis, jOOQ, Spring Data JPA, MongoTemplate

#### drivers-and-connection-pooling
**Label:** Drivers & connection pooling
**Def:** Wire-protocol clients and the pools in front of them.
**Seeds:** JDBC, HikariCP, psycopg, asyncpg, pgbouncer, Lettuce, MongoDB Java Driver

#### schema-and-migration
**Label:** Schema & migration
**Def:** Versioned schema change, zero-downtime rollout, partition management.
**Seeds:** Flyway, Liquibase, Alembic, Skeema, pg_partman, gh-ost, pgTAP

#### replication-ha-and-backup
**Label:** Replication, HA & backup
**Def:** Failover, read replicas, sharding topology, backup and restore.
**Seeds:** Patroni, Orchestrator, MHA, Vitess, pgBackRest, Velero, WAL-G

#### transactions-and-consistency
**Label:** Transactions & consistency
**Def:** Isolation, 2PC/XA, consensus and coordination services, distributed-transaction machinery.
**Seeds:** ZooKeeper, etcd, Seata, Paxos, Raft, @Transactional, Percolator

### caching
**Label:** Caching
**Blurb:** Making the second read cheap - in process, over the network, at the edge, or in front of a model.

#### in-process-cache
**Label:** In-process cache
**Def:** A cache inside the application heap.
**Seeds:** Caffeine, Guava Cache, Ehcache, functools.lru_cache, cachetools, weigher

#### distributed-cache
**Label:** Distributed cache & shared state
**Def:** A cache or shared-state store other processes reach over the network. Session stores live here.
**Seeds:** Redis, Memcached, Hazelcast, ElastiCache, Redisson, Spring Session
**Merged:** 'session store' folded in - ~12 tools, and every one of them is a Redis/cookie shared-state decision.

#### http-and-cdn-cache
**Label:** HTTP & CDN cache
**Def:** Cache at the HTTP layer or the edge PoP: validators, reverse-proxy cache, CDN.
**Seeds:** CloudFront, Cloudflare, Fastly, Varnish, nginx proxy_cache, ETag

#### semantic-and-llm-cache
**Label:** Prompt & semantic cache
**Def:** Reuse of model work: KV-prefix caches, exact prompt caches, embedding-similarity caches.
**Seeds:** vLLM APC, SGLang RadixAttention, GPTCache, Portkey cache, Anthropic prompt caching

### data-movement
**Label:** Queues & streaming
**Blurb:** Getting data from where it happened to where it is processed.

#### message-broker
**Label:** Message broker
**Def:** Queue and pub/sub brokers with per-message delivery semantics.
**Seeds:** RabbitMQ, AWS SQS, SNS, NATS, ActiveMQ, Azure Service Bus, MQTT

#### event-streaming-and-processing
**Label:** Event streaming & stream processing
**Def:** The durable log, its ecosystem, the stream processors on top, and CDC into it.
**Seeds:** Kafka, Redpanda, Kinesis, Pulsar, Flink, Kafka Streams, ksqlDB, Debezium, Schema Registry
**Merged:** 'stream processing' (3), 'CDC' (5) and 'event sourcing' (4) were each under the 8-tool floor and all three are the same log.

#### task-queue-and-jobs
**Label:** Task queue & background jobs
**Def:** Application-level job dispatch, workers, retries, scheduling of work units.
**Seeds:** Celery, Sidekiq, RQ, Dramatiq, arq, Quartz, BackgroundTasks, KEDA

#### workflow-and-durable-execution
**Label:** Workflow & durable execution
**Def:** DAG schedulers and durable/saga orchestrators - anything that survives a restart mid-flow.
**Seeds:** Airflow, Dagster, Prefect, Argo Workflows, Temporal, Cadence, Conductor OSS, Step Functions

#### batch-and-distributed-compute
**Label:** Batch & distributed compute
**Def:** Large-scale processing engines and the dataframe/analytics libraries used on top.
**Seeds:** Spark, Ray, Dask, Hadoop, Beam, pandas, Polars, Arrow

#### data-quality-and-lineage
**Label:** Data quality & lineage
**Def:** Expectation suites, contract checks, versioning and provenance of datasets.
**Seeds:** Great Expectations, Soda, Pandera, DVC, LakeFS, Pachyderm, OpenLineage

### search-retrieval
**Label:** Search & retrieval
**Blurb:** Finding the right passage: lexical, dense, hybrid, then reranked and assembled.

#### ann-index-library
**Label:** ANN & embedding index
**Def:** In-process nearest-neighbour index libraries, and the embedding-side libraries that feed them. The SERVICE form is data-stores/vector-store.
**Seeds:** FAISS, ScaNN, HNSWlib, Annoy, DiskANN, sentence-transformers, USearch

#### lexical-and-hybrid-search
**Label:** Lexical & hybrid search
**Def:** Inverted-index engines, BM25/sparse retrieval, fusion of sparse and dense.
**Seeds:** Elasticsearch, OpenSearch, Lucene, Typesense, rank_bm25, SPLADE, RRF

#### reranking
**Label:** Reranking
**Def:** Second-stage scoring over a candidate set.
**Seeds:** Cohere Rerank, bge-reranker, ColBERT, cross-encoders, MonoT5, RankGPT

#### rag-and-document-processing
**Label:** RAG pipelines & document processing
**Def:** Retrieval-augmented pipelines end to end, plus the parsing/chunking that produces their corpus.
**Seeds:** LlamaIndex, Haystack, GraphRAG, RAPTOR, Unstructured, LlamaParse, Docling, PyMuPDF
**Merged:** 'document parsing' (3) folded into RAG - in this repo it is always the ingest stage of a RAG pipeline.

### llm-apps
**Label:** LLM apps & agents
**Blurb:** Building on top of a model: agents, tools, prompts, gateways, environments.

#### agent-framework
**Label:** Agent & multi-agent framework
**Def:** Loop/graph runtimes for tool-using agents, including multi-agent topologies and handoff protocols.
**Seeds:** LangGraph, CrewAI, AutoGen, smolagents, OpenAI Agents SDK, A2A, Strands, PydanticAI
**Merged:** 'multi-agent' kept as a synonym rather than a role - in the data it is always the same framework used in a different topology.

#### tool-use-and-mcp
**Label:** Tool use & MCP
**Def:** Function calling, tool schemas, MCP servers/clients/registries, agent tool catalogues.
**Seeds:** mcp Python SDK, MCP Hub, @modelcontextprotocol/server-everything, LangChain MCP adapter, OpenAI function calling

#### prompting-context-and-structured-output
**Label:** Prompting, context & structured output
**Def:** Prompt authoring/management/optimization, context assembly, agent memory, long-context tricks, and constrained decoding into a schema.
**Seeds:** DSPy, Instructor, Outlines, Guidance, Mem0, LangMem, Zep, YaRN

#### llm-gateway-and-routing
**Label:** LLM gateway & routing
**Def:** One endpoint over many providers: routing, fallback, budget, key management.
**Seeds:** LiteLLM, Portkey, OpenRouter, Bedrock, semantic router

#### agentic-environments
**Label:** Agent environments & coding agents
**Def:** Where an agent acts: browsers, computers, sandboxes, repositories.
**Seeds:** Playwright, browser-use, Skyvern, OpenHands, SWE-agent, Aider, E2B, Anthropic Computer Use

### model-training
**Label:** Model training
**Blurb:** Producing the weights: frameworks, algorithms, adaptation, scale-out.

#### deep-learning-framework
**Label:** Deep-learning framework
**Def:** Tensor/autograd frameworks and their training loops.
**Seeds:** PyTorch, TensorFlow, JAX, Keras, Lightning, Flax, torch.optim

#### classical-ml-and-boosting
**Label:** Classical ML & boosting
**Def:** Non-deep supervised/unsupervised learning, gradient-boosted trees, statistical fitting.
**Seeds:** scikit-learn, XGBoost, LightGBM, CatBoost, H2O, statsmodels

#### fine-tuning-and-peft
**Label:** Fine-tuning & PEFT
**Def:** Adapting a pretrained model: SFT, LoRA/QLoRA, adapters, merging, distillation-for-training.
**Seeds:** PEFT, TRL, Axolotl, Unsloth, LLaMA-Factory, torchtune, mergekit

#### alignment-and-rl
**Label:** Alignment & reinforcement learning
**Def:** Preference optimization, reward models, RLHF/RLAIF, and general RL toolkits.
**Seeds:** TRL DPO, OpenRLHF, verl, Gymnasium, Stable-Baselines3, RLlib, Math-Shepherd

#### distributed-training
**Label:** Distributed training
**Def:** Sharding a training job over many devices or hosts.
**Seeds:** DeepSpeed, FSDP, Megatron-LM, Accelerate, Horovod, Ray Train, XGBoost Dask

### ml-lifecycle
**Label:** MLOps & evaluation
**Blurb:** Everything around the model: tracking, platforms, labels, drift, and proof that it works.

#### experiment-tracking-and-tuning
**Label:** Experiment tracking, tuning & registry
**Def:** Run/metric tracking, sweeps and HPO, model registry and versioning.
**Seeds:** MLflow, Weights & Biases, Neptune, Optuna, Ray Tune, AutoGluon, model registry

#### ml-platform-and-pipelines
**Label:** ML platform & feature store
**Def:** The managed surface a team trains and ships on, and the online/offline feature layer.
**Seeds:** Kubeflow, SageMaker, Vertex AI, Metaflow, ZenML, Feast, Tecton

#### labeling-and-synthetic-data
**Label:** Labeling & synthetic data
**Def:** Producing training targets: annotation tools, weak supervision, generated corpora.
**Seeds:** Label Studio, Argilla, Snorkel, Prodigy, distilabel, Alpaca farm

#### drift-and-production-monitoring
**Label:** Drift & production ML monitoring
**Def:** Watching a deployed model: data/concept drift, unlabelled performance estimation, quality alerts.
**Seeds:** Evidently, NannyML, WhyLabs, Arize, Fiddler AI, alibi-detect, Deepchecks

#### evaluation-and-benchmarks
**Label:** Evaluation, benchmarks & experiments
**Def:** Offline eval harnesses, metric libraries, the benchmark corpora themselves, and online A/B experimentation.
**Seeds:** lm-eval-harness, RAGAS, DeepEval, promptfoo, MMLU, GSM8K, seqeval, Optimizely, Statsig
**Merged:** 'benchmark/dataset' (82) stays inside evaluation because the kind axis already isolates it (kind == dataset); a second role would duplicate that filter.

### applied-ml
**Label:** Applied ML
**Blurb:** Domain toolkits - what you reach for once you know the problem shape.

#### nlp-and-text
**Label:** NLP & text
**Def:** Tokenization, tagging, NER, classical and transformer NLP pipelines.
**Seeds:** spaCy, NLTK, Hugging Face Transformers, gensim, tokenizers, fastText, evalb

#### vision-speech-and-multimodal
**Label:** Vision, speech & multimodal
**Def:** Images, video, audio, and joint text-image-audio models and toolkits.
**Seeds:** OpenCV, torchvision, YOLO, segment-anything, CLIP, Whisper, ElevenLabs, pyannote
**Merged:** 'speech' (15) merged with vision - both are the perception stack, and the multimodal models already straddle them.

#### recommenders-and-graph-ml
**Label:** Recommenders & graph ML
**Def:** Ranking, retrieval-and-rank recsys, and graph neural networks.
**Seeds:** TorchRec, LightFM, implicit, PyG, DGL, node2vec, Neo4j GDS

#### timeseries-and-anomaly
**Label:** Forecasting & anomaly detection
**Def:** Sequence forecasting and outlier detection over signals.
**Seeds:** Prophet, statsforecast, sktime, Darts, ADTK, PyOD

#### interpretability-fairness-and-causal
**Label:** Interpretability, fairness & causal
**Def:** Explaining a model, auditing it, quantifying uncertainty, and estimating effects rather than correlations.
**Seeds:** SHAP, LIME, captum, Fairlearn, TransformerLens, SAELens, DoWhy, EconML, PyMC

### inference
**Label:** Inference & optimization
**Blurb:** Serving the model fast and small.

#### inference-engine
**Label:** LLM inference engine
**Def:** Batching/paged-attention/decoding runtimes for autoregressive models.
**Seeds:** vLLM, TGI, SGLang, llama.cpp, TensorRT-LLM, LMDeploy, Ollama

#### model-server
**Label:** Model server
**Def:** Generic serving surfaces: endpoints, versioning, inference graphs, autoscaling of replicas.
**Seeds:** Triton, TorchServe, TF Serving, KServe, Seldon Core, BentoML, Ray Serve

#### quantization-and-compression
**Label:** Quantization & compression
**Def:** Shrinking weights and activations: PTQ/QAT, pruning, sparsity, low-bit formats.
**Seeds:** GPTQ, AWQ, bitsandbytes, SmoothQuant, llm-compressor, torch.ao

#### compiler-and-runtime-optimization
**Label:** Graph compiler & runtime
**Def:** Ahead-of-time and JIT graph compilation, kernel fusion, vendor runtimes.
**Seeds:** ONNX Runtime, TorchInductor, TVM, OpenVINO, XLA, TensorRT, torch.compile

#### model-format-and-edge
**Label:** Model formats & edge deployment
**Def:** Interchange formats, export toolchains, and on-device/edge targets.
**Seeds:** ONNX, GGUF, safetensors, coremltools, TFLite, ExecuTorch, optimum-intel, Jetson

### gpu
**Label:** GPU & parallel
**Blurb:** Programming the accelerator itself.

#### kernel-programming
**Label:** Kernel programming
**Def:** Authoring device code and managing its memory/launch: CUDA C++ and every API around it, kernel DSLs, Python kernel entry points.
**Seeds:** CUDA C++, Triton, Numba, CuPy RawKernel, cudaHostAlloc, Cooperative Groups, cudaLaunchCooperativeKernel

#### gpu-math-libraries
**Label:** GPU math & DNN libraries
**Def:** Vendor-tuned BLAS/FFT/DNN/primitive libraries you call instead of writing the kernel.
**Seeds:** cuBLAS, cuDNN, CUTLASS, cuFFT, Thrust, CUB, rocBLAS

#### multi-gpu-and-collectives
**Label:** Multi-GPU & collectives
**Def:** Spanning devices and nodes: collectives, interconnect, launchers, partitioning.
**Seeds:** NCCL, NVLink, mpirun, NVSHMEM, NVIDIA device plugin, MIG

#### gpu-profiling-and-debugging
**Label:** GPU profiling & debugging
**Def:** Finding out what the GPU actually did, and what it did wrong.
**Seeds:** Nsight Systems, Nsight Compute, cuda-gdb, compute-sanitizer, cudaEvent_t timers, py3nvml, NVTX

#### gpu-portability-and-precision
**Label:** Portability & precision
**Def:** Running the same compute on other vendors, and choosing the numeric format that keeps the tensor cores busy.
**Seeds:** HIP, SYCL, OpenCL, wgpu, Metal Performance Shaders, hipify-clang, Transformer Engine, FP8, TF32

### apis-frameworks
**Label:** APIs & app frameworks
**Blurb:** How a service is exposed and how its code is wired together.

#### web-framework-and-http-client
**Label:** Web framework & HTTP client
**Def:** Server-side routing/handler frameworks, app servers, and the clients that call them.
**Seeds:** Spring MVC, FastAPI, Starlette, Flask, WebClient, httpx, OkHttp, uvicorn

#### rpc-graphql-and-streaming
**Label:** RPC, GraphQL & streaming transport
**Def:** Non-plain-REST call shapes: gRPC/Thrift, GraphQL, WebSocket/SSE/RSocket.
**Seeds:** gRPC, protobuf, RSocket, grpc-gateway, Apollo, Relay Connection, WebSocket, SSE

#### data-formats-and-api-contracts
**Label:** Data formats & API contracts
**Def:** Wire/serialization formats and the specs, schemas and validators that pin a contract down.
**Seeds:** Jackson, Avro, MessagePack, OpenAPI, JSON Schema, Pact, Pydantic, Bean Validation

#### dependency-injection-and-config
**Label:** Dependency injection & configuration
**Def:** Container wiring, bean lifecycle, property sources, profiles, external config.
**Seeds:** @Bean, @ComponentScan, Guice, Dagger, @ConfigurationProperties, EnvironmentPostProcessor, pydantic-settings

#### aop-middleware-and-scheduling
**Label:** AOP, middleware & scheduled work
**Def:** Cross-cutting interception - aspects, filters, ASGI middleware, interceptors - plus in-app batch and scheduled jobs.
**Seeds:** Spring AOP, AspectJ, jakarta.servlet.Filter, BaseHTTPMiddleware, @Scheduled, Spring Batch, CGLIB

#### design-patterns-and-principles
**Label:** Design patterns & principles
**Def:** GoF and architectural patterns, SOLID-style principles, anti-patterns, and the stdlib/framework hooks that embody them.
**Seeds:** Abstract Factory, Strategy, Chain of Responsibility, Law of Demeter, Anemic Domain Model, SimpleFileVisitor, Guava EventBus
**Note:** This is the home for the ~110 kind=concept entries the lld section contributes. They are hidden from the default view by the kind axis, not by tier.

### traffic-edge
**Label:** Traffic & resilience
**Blurb:** What sits in front of the service and what happens when it misbehaves.

#### api-gateway
**Label:** API gateway
**Def:** The single ingress that authenticates, aggregates, and applies policy.
**Seeds:** Kong, AWS API Gateway, Spring Cloud Gateway, Apigee, Tyk, Zuul

#### proxy-and-load-balancer
**Label:** Proxy & load balancer
**Def:** L4/L7 traffic distribution and reverse proxying.
**Seeds:** nginx, HAProxy, Envoy, Traefik, ALB, MetalLB, IPVS

#### service-mesh-and-discovery
**Label:** Service mesh & discovery
**Def:** Sidecar/ambient traffic control and how a service finds another one, DNS included.
**Seeds:** Istio, Linkerd, Consul, Eureka, CoreDNS, Cilium, Kubernetes Services

#### rate-limiting-and-resilience
**Label:** Rate limiting & resilience
**Def:** Protecting a dependency and surviving its failure: quotas, retries, breakers, bulkheads, shedding.
**Seeds:** Resilience4j, Bucket4j, Envoy ratelimit, Redis sliding window, Hystrix, Failsafe

### observability
**Label:** Observability
**Blurb:** Knowing what the system did, and being told when it stops.

#### metrics-and-monitoring
**Label:** Metrics & monitoring
**Def:** Counters/gauges/histograms, exporters, scrape and query.
**Seeds:** Prometheus, Micrometer, Grafana Mimir, cAdvisor, Blackbox exporter, StatsD

#### tracing-apm-and-llm-observability
**Label:** Tracing, APM & LLM observability
**Def:** Span-level causality across services, application performance monitoring, and the LLM-specific trace/eval tooling built on the same idea.
**Seeds:** OpenTelemetry, Jaeger, Zipkin, Datadog APM, AWS X-Ray, Langfuse, LangSmith, Arize Phoenix

#### logging
**Label:** Logging
**Def:** Structured emission, shipping, aggregation, and query of log lines.
**Seeds:** Logback, SLF4J, Log4j2, Loki, Fluentd, structlog, ELK

#### profiling-and-performance
**Label:** Profiling & performance analysis
**Def:** CPU/memory/lock profilers, flame graphs, allocation and query-plan analysis.
**Seeds:** async-profiler, py-spy, perf, JFR, VisualVM, memory_profiler, EXPLAIN, pg_stat_statements
**Note:** Database plan/stat tooling (EXPLAIN, pg_stat_statements, SHOW ENGINE INNODB STATUS) lands here as well as in data-access; that is the multi-label model working.

#### alerting-and-incident-response
**Label:** Alerting, dashboards & incidents
**Def:** Turning signal into a page, and running the response: dashboards, alert rules, on-call, SLOs, postmortems.
**Seeds:** Grafana, Alertmanager, PagerDuty, Opsgenie, Pyrra, Jeli, Slack war room

### security
**Label:** Security & identity
**Blurb:** Who may do what, what stays secret, and what an attacker can reach.

#### authentication-and-identity
**Label:** Authentication & identity
**Def:** Proving who the caller is: OAuth/OIDC flows, tokens, IdPs, sessions, MFA.
**Seeds:** Keycloak, Auth0, Entra ID, PyJWT, authlib, OAuth 2.1, Spring Security OAuth2

#### authorization-and-policy
**Label:** Authorization & policy
**Def:** Deciding what the caller may do, as code: RBAC/ABAC engines, admission and org policy.
**Seeds:** OPA, Casbin, Cedar, Kyverno, IAM, IRSA

#### secrets-and-cryptography
**Label:** Secrets, keys & cryptography
**Def:** Storing and rotating secrets, and the primitives/PKI underneath.
**Seeds:** HashiCorp Vault, AWS Secrets Manager, SOPS, cert-manager, OpenSSL, argon2-cffi, BouncyCastle

#### supply-chain-and-runtime-security
**Label:** Supply chain & runtime security
**Def:** Trusting what you ship and containing what you run: SBOM, signing, CVE scanning, DAST/SAST, sandboxing and runtime detection.
**Seeds:** Trivy, Grype, Syft, Sigstore, Semgrep, Burp Suite, Falco, gVisor, seccomp
**Merged:** 'container security' (8) folded in - the same team, the same pipeline stage, and half the tools appear in both.

#### privacy-and-compliance
**Label:** Privacy & compliance
**Def:** PII handling, anonymization, differential privacy, encrypted computation, regulated-data controls.
**Seeds:** Presidio, Opacus, TenSEAL, differential privacy, GDPR controls, data masking

#### ai-safety-and-guardrails
**Label:** AI safety, guardrails & red teaming
**Def:** Constraining and attacking model behaviour: content filters, jailbreak defence, prompt-injection testing, adversarial robustness.
**Seeds:** Llama Guard, NeMo Guardrails, Bedrock Guardrails, garak, EasyJailbreak, llm-attacks, SmoothLLM, AutoAttack

### platform-delivery
**Label:** Deploy & cloud
**Blurb:** Packaging it, scheduling it, shipping it, and paying for it.

#### container-and-image
**Label:** Containers & images
**Def:** Runtimes, image build, layers, registries of images.
**Seeds:** Docker, containerd, Podman, BuildKit, buildx, dive, distroless, Harbor

#### kubernetes-and-orchestration
**Label:** Kubernetes & orchestration
**Def:** Cluster scheduling and the object model on top: workloads, operators/CRDs, packaging, autoscaling, cluster networking.
**Seeds:** Kubernetes, Helm, Kustomize, KEDA, Karpenter, OLM, GKE, Nomad

#### ci-cd-and-release
**Label:** CI/CD & release
**Def:** Pipelines, GitOps reconciliation, progressive delivery, feature flags.
**Seeds:** GitHub Actions, Jenkins, Argo CD, Flux, Spinnaker, Unleash, Renovate

#### infrastructure-as-code-and-config
**Label:** Infrastructure as code & config
**Def:** Declaring infrastructure and machine state.
**Seeds:** Terraform, Pulumi, CloudFormation, Ansible, Puppet, Packer, Crossplane

#### cloud-platform-and-cost
**Label:** Cloud platform, serverless & cost
**Def:** Managed cloud surfaces including FaaS and GPU capacity, plus what they cost.
**Seeds:** AWS Lambda, Cloud Run, Fargate, cloud SDKs, Infracost, Kubecost, OpenCost

### devtools
**Label:** Build & test tooling
**Blurb:** The loop between writing code and trusting it.

#### build-and-dependency-management
**Label:** Build & dependency management
**Def:** Build systems, dependency resolution, packaging and publishing of your own artifacts.
**Seeds:** Maven, Gradle, Bazel, uv, Poetry, pip, twine, CMake

#### compiler-toolchain-and-codegen
**Label:** Compilers, toolchains & codegen
**Def:** Turning source into something runnable, and generating source: compilers, native-image toolchains, annotation processors, bytecode manipulation, parser generators.
**Seeds:** javac, GraalVM native-image, Clang, Cython, Lombok, ByteBuddy, ASM, ANTLR, protoc, KAPT
**Merged:** 'codegen'/'bytecode'/'annotation processing' merged into the toolchain role - all three are build-time source or class transformation.

#### static-analysis-and-linting
**Label:** Static analysis & formatting
**Def:** Reading code without running it: linters, formatters, type checkers, quality gates.
**Seeds:** ruff, SonarQube, Checkstyle, SpotBugs, PMD, mypy, pre-commit

#### testing-and-mocking
**Label:** Testing, mocking & load
**Def:** Every kind of executed test: unit/integration/property/contract frameworks, doubles and fixtures, load, stress and chaos.
**Seeds:** JUnit, pytest, AssertJ, Mockito, Testcontainers, WireMock, k6, Locust, Chaos Mesh, PIT
**Merged:** 'load testing' (12), 'chaos engineering' (9) and four sub-8 testing subs collapsed into one role - the tier chip is the useful filter here, not six near-empty ones.

#### version-control-and-workbench
**Label:** Version control & workbench
**Def:** Source control workflow plus the editor/notebook/CLI environment the work happens in.
**Seeds:** git, GitHub, Conventional Commits, IntelliJ IDEA, VS Code, Jupyter, jq, tmux

### runtime-systems
**Label:** Runtime & OS
**Blurb:** The language runtime and the machine under it. Mostly kind=api, so hidden by default.

#### concurrency-and-async
**Label:** Concurrency & async
**Def:** Threads, locks, executors, virtual threads, coroutines, event loops, reactive streams.
**Seeds:** java.util.concurrent, Virtual threads, asyncio, anyio, Project Reactor, threading.Condition, VarHandle

#### memory-processes-and-os
**Label:** Memory, processes & OS
**Def:** Allocation and GC, virtual memory and NUMA, process/thread scheduling, cgroups, syscall-level resource control, CPU architecture.
**Seeds:** G1, ZGC, jemalloc, numactl, cgroups cpu.cfs_quota_us, /proc/<pid>/sched, perf counters

#### collections-and-algorithms
**Label:** Collections, algorithms & math
**Def:** Data structures and their operations, sorting/searching, complexity tooling, and numeric/statistical libraries.
**Seeds:** java.util.TreeMap, IntStream, heapq, Arrays.sort, NumPy, SciPy stats, big-O Python library, Z3

#### text-encoding-and-regex
**Label:** Text, encoding & regex
**Def:** Character sets, normalization, codecs, string APIs, pattern matching.
**Seeds:** Unicode Character Database, codecs, re, RE2/J, TextDecoder, Text Blocks

#### io-networking-and-syscalls
**Label:** I/O, filesystem & network stack
**Def:** File and socket I/O, memory mapping, the TCP/IP stack, and the tools that inspect it.
**Seeds:** FileChannel.map, pathlib, epoll, netstat -s, tcpdump, Wireshark, nmap, iptables, openssl s_client

#### runtime-internals-and-types
**Label:** Runtime internals & type system
**Def:** How the VM/interpreter actually works, plus the static type layer: reflection, class loading, modules, JIT, CPython internals, typing constructs.
**Seeds:** CPython internals, JITWatch, JPMS, ServiceLoader, typing.Protocol, typing_extensions, dataclasses, jdeprscan
