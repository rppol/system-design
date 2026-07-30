# Search & retrieval — technology bank

<!-- tech-bank tier: search-retrieval -->

The 91 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Search & retrieval** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### _cat/indices
**Short:** Elasticsearch/OpenSearch cat API returning per-index health, doc counts and store size as readable rows.
**Kind:** api
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, observability/metrics-and-monitoring @3

### _cat/shards
**Short:** Elasticsearch admin endpoint listing every shard with its node, state and size; first stop for allocation bugs.
**Kind:** api
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-access/replication-ha-and-backup @2, observability/metrics-and-monitoring @3

### Amazon OpenSearch
**Short:** AWS-managed OpenSearch: inverted-index search and log analytics, with a k-NN plugin for vector and hybrid retrieval.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/vector-store @2, observability/logging @3, platform-delivery/cloud-platform-and-cost @3

### ANN retrieval
**Short:** Approximate nearest-neighbour search over embeddings, served by libraries like FAISS or managed vector databases.
**Kind:** concept
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, data-stores/vector-store @2

### Annoy
**Short:** Spotify's tree-based approximate nearest-neighbour library; memory-mapped, read-only after build.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/ann-index-library @1, applied-ml/recommenders-and-graph-ml @3

### Anserini
**Short:** Lucene-based toolkit for reproducible sparse retrieval baselines (BM25, SPLADE) on IR benchmark collections.
**Kind:** tech
**Lang:** java
**Roles:** search-retrieval/lexical-and-hybrid-search @1, ml-lifecycle/evaluation-and-benchmarks @3

### BAAI/bge
**Short:** BAAI's open BGE text-embedding family: strong open-source English retrieval embeddings in several sizes.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/nlp-and-text @3

### Bedrock Knowledge Base
**Short:** AWS Bedrock managed RAG: ingests S3 documents, chunks and embeds them, and answers queries with citations.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/vector-store @3, platform-delivery/cloud-platform-and-cost @3

### BGE
**Short:** BAAI's open text-embedding family for dense and hybrid retrieval, with matching cross-encoder reranker models.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, search-retrieval/reranking @3

### BGE-reranker
**Short:** Open-weight cross-encoder from BAAI that rescores retrieved passages against the query; free to self-host.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

### BGE-reranker-large
**Short:** BAAI open cross-encoder reranker rescoring query-document pairs; 512-token limit, best open-source quality.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

### BGE-reranker-v2-m3
**Short:** Open multilingual cross-encoder that rescores retrieved passages against the query in a second stage.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

### Casetext CoCounsel
**Short:** Legal AI assistant doing retrieval-augmented research and document review over Westlaw case law.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @3

### chroma-haystack
**Short:** Haystack integration package wiring ChromaDB in as the document and embedding store for a RAG pipeline.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/vector-store @2

### Cohere
**Short:** Hosted model provider whose Rerank API rescoring a candidate list is the common second stage in RAG.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/reranking @1, applied-ml/nlp-and-text @3

### Cohere Embed
**Short:** Cohere's hosted text embedding models for dense retrieval, with multilingual and compressed variants.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, applied-ml/nlp-and-text @2

### Cohere Embed API
**Short:** Cohere's managed embedding endpoint (embed-v4.0): multimodal inputs, 128k context and truncatable Matryoshka dims.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/nlp-and-text @3

### Cohere Rerank
**Short:** Managed cross-encoder reranking API that rescores retrieved documents against the query before generation.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

### Cohere Rerank 3.5
**Short:** Managed cross-encoder reranker that rescores retrieved passages; 100+ languages and a 4096-token context.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

### Cohere Rerank API
**Short:** Managed cross-encoder endpoint that rescores retrieved documents against the query and returns relevance scores.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/reranking @1

### ColBERT
**Short:** Late-interaction retrieval model scoring per-token embeddings with MaxSim: near cross-encoder quality, lower latency.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1, search-retrieval/lexical-and-hybrid-search @2, search-retrieval/ann-index-library @3, applied-ml/nlp-and-text @3

### cross-encoder/ms-marco-MiniLM-L-6-v2
**Short:** Small 6-layer cross-encoder trained on MS MARCO; a fast self-hosted relevance reranker for retrieved passages.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

### deepset Cloud
**Short:** Hosted deployment and evaluation surface for Haystack RAG pipelines.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, platform-delivery/cloud-platform-and-cost @3

### dspy.Retrieve
**Short:** DSPy module fetching passages from a configured retriever (Chroma, Pinecone, Weaviate, ColBERT).
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/prompting-context-and-structured-output @2

### E5
**Short:** Microsoft's E5 text-embedding family for dense retrieval, trained with query: and passage: input prefixes.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2

### Elastic Cloud
**Short:** Elastic's managed hosting for Elasticsearch and Kibana: provisioned clusters with upgrades and scaling handled.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, platform-delivery/cloud-platform-and-cost @3, observability/logging @3

### Elasticsearch
**Short:** Distributed inverted-index search engine for full-text/BM25, hybrid dense+sparse retrieval, and log analytics.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/document @2, observability/logging @2, data-stores/vector-store @3, data-stores/warehouse-and-olap @3

### Elasticsearch/OpenSearch
**Short:** Distributed inverted-index search engine and JSON document store, widely used as the log-aggregation backend.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, observability/logging @2, data-stores/document @2, data-stores/vector-store @3

### Embedding model
**Short:** A text encoder producing fixed-length vectors for similarity search, e.g. all-MiniLM-L6-v2 at 384 dims on CPU.
**Kind:** concept
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, applied-ml/nlp-and-text @2

### explain API
**Short:** Elasticsearch/OpenSearch endpoint returning the scoring breakdown for one document against one query.
**Kind:** api
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, observability/profiling-and-performance @3

### FAISS
**Short:** Meta's nearest-neighbour search library (flat, IVF, HNSW, PQ; CPU and GPU) for billion-scale vector retrieval.
**Kind:** tech
**Lang:** python, cpp
**Roles:** search-retrieval/ann-index-library @1, data-stores/vector-store @3, applied-ml/recommenders-and-graph-ml @3, runtime-systems/collections-and-algorithms @3

### FlashRank
**Short:** Tiny, fast cross-encoder reranking library for resource-constrained RAG deployments.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/reranking @1

### Gemini Embedding API
**Short:** Google's hosted multimodal embedding endpoint placing text, image, audio and PDF into one vector space.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/vision-speech-and-multimodal @3

### GraphRAG
**Short:** Microsoft's open-source pipeline that builds an entity/community knowledge graph from a corpus and queries it for RAG.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/graph-db @3

### Harvey AI
**Short:** Vertical legal AI product for contract review, legal research and drafting over a firm's document corpus.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @3

### Haystack
**Short:** deepset's pipeline framework for production RAG and NLP: composable retrievers, readers, generators and agents.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @2, applied-ml/nlp-and-text @3

### haystack-ai
**Short:** Deepset's pipeline framework for composing retrievers, rankers, prompt builders and generators into RAG apps.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @2, llm-apps/prompting-context-and-structured-output @3

### Hnswlib
**Short:** Header-only HNSW approximate-nearest-neighbour index library; best recall per query, entire index held in RAM.
**Kind:** tech
**Lang:** python, cpp
**Roles:** search-retrieval/ann-index-library @1

### Jina Embeddings
**Short:** Open-weight embedding model family with long context windows and multilingual variants for retrieval.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, applied-ml/nlp-and-text @3

### Jina Reranker
**Short:** Jina's cross-encoder reranking models, available hosted or open-weight, including multilingual and image+text variants.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1, applied-ml/vision-speech-and-multimodal @3

### JPMorgan COiN
**Short:** JPMorgan's internal contract-intelligence system that extracts clauses and terms from loan agreements.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, applied-ml/nlp-and-text @2

### LangChain CrossEncoderReranker
**Short:** LangChain document compressor wrapping a Hugging Face cross-encoder to rescore and trim retrieved candidates.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/reranking @1, search-retrieval/rag-and-document-processing @2

### LangChain Graph RAG
**Short:** LangChain's graph retrieval path: generates Cypher against Neo4j and grounds answers in the returned subgraph.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/graph-db @2

### LangChain RecursiveCharacterTextSplitter
**Short:** LangChain chunker splitting on paragraph, then sentence, then character boundaries until chunks fit the limit.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1

### llama-index-core
**Short:** LlamaIndex's base package: document loading, node parsing, indices, retrievers and query engines for RAG.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @2, search-retrieval/ann-index-library @3

### llama-index-embeddings-openai
**Short:** LlamaIndex integration package that calls OpenAI embedding models to vectorize nodes and queries.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2

### llama-index-llms-openai
**Short:** LlamaIndex integration package binding OpenAI models into its query and agent pipelines, one package per provider.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/llm-gateway-and-routing @2

### llama-index-postprocessor-cohere-rerank
**Short:** LlamaIndex node postprocessor that reorders retrieved chunks through the Cohere Rerank API before they hit the prompt.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/reranking @1, search-retrieval/rag-and-document-processing @2

### llama-index-retrievers-bm25
**Short:** LlamaIndex integration package providing an in-process BM25 retriever (via rank_bm25) for hybrid retrieval.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/rag-and-document-processing @2

### LlamaCloud
**Short:** LlamaIndex's managed ingestion and parsing service, handling tables, images and complex PDFs into a queryable index.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, ml-lifecycle/ml-platform-and-pipelines @3

### LlamaHub
**Short:** LlamaIndex's community registry of 150+ data connectors, vector-store adapters, LLM integrations and tool packs.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/tool-use-and-mcp @3

### LlamaIndex
**Short:** RAG-first framework: ingestion, chunking, indexes, query transforms and retrieval-driven agents over your data.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @2, llm-apps/prompting-context-and-structured-output @3, search-retrieval/ann-index-library @3

### LlamaIndex HierarchicalNodeParser
**Short:** LlamaIndex parser producing parent-child chunk hierarchies so retrieval can match small nodes but return larger context.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1

### LlamaIndex MultiModalVectorStoreIndex
**Short:** LlamaIndex index type that stores and retrieves text and image nodes together for multimodal RAG.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, applied-ml/vision-speech-and-multimodal @2

### LlamaIndex PropertyGraphIndex
**Short:** LlamaIndex index that extracts a property graph from documents and retrieves over it for graph RAG.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/graph-db @2, applied-ml/recommenders-and-graph-ml @3

### LlamaIndex SemanticSplitterNodeParser
**Short:** LlamaIndex chunker placing boundaries where consecutive sentence embeddings diverge, not at fixed token counts.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1

### LlamaIndex SentenceSplitter
**Short:** LlamaIndex chunker that splits documents at sentence boundaries with a configurable chunk size and overlap.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1

### LlamaIndex SentenceTransformerRerank
**Short:** LlamaIndex postprocessor that rescores retrieved nodes with a local cross-encoder before they reach the model.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/reranking @1, search-retrieval/rag-and-document-processing @2

### LlamaIndex VectorStoreIndex
**Short:** LlamaIndex abstraction embedding documents into a pluggable vector store and serving retrievers and query engines.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, search-retrieval/ann-index-library @2, data-stores/vector-store @3

### LLM-Blender
**Short:** Ensembling framework that pairwise-ranks candidate responses from several LLMs and fuses the best.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/reranking @1, ml-lifecycle/labeling-and-synthetic-data @2, llm-apps/llm-gateway-and-routing @3

### Lucene
**Short:** The Java inverted-index library behind Elasticsearch and Solr: analyzers, postings lists, BM25 and HNSW vectors.
**Kind:** tech
**Lang:** java
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/ann-index-library @3

### Meilisearch
**Short:** Lightweight search engine focused on instant typo-tolerant full-text results, with optional hybrid vector search.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/vector-store @3

### Microsoft GraphRAG
**Short:** Microsoft's open-source graph RAG pipeline: entity extraction, Leiden communities and community summaries.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/graph-db @3, applied-ml/recommenders-and-graph-ml @3

### ms-marco-MiniLM
**Short:** Small cross-encoder reranker trained on MS MARCO; rescores query-passage pairs after first-stage retrieval.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

### nomic-embed-text
**Short:** Fully open-weight text embedding model with an 8192-token context, a practical self-hosted default for RAG.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/nlp-and-text @3

### OpenAI embeddings
**Short:** OpenAI's hosted text embedding endpoints producing dense vectors for semantic search, clustering and RAG retrieval.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/nlp-and-text @3

### OpenAI Embeddings API
**Short:** OpenAI's hosted text embedding endpoint (text-embedding-3-small/large) producing vectors for semantic search and RAG.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, llm-apps/llm-gateway-and-routing @3

### OpenSearch
**Short:** Distributed search and analytics engine forked from Elasticsearch; BM25, log storage and a neural/hybrid search plugin.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, observability/logging @2, data-stores/vector-store @2

### opensearch-haystack
**Short:** Haystack integration using OpenSearch as the document store, supporting BM25, kNN and hybrid retrieval.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, search-retrieval/lexical-and-hybrid-search @2

### pdfplumber
**Short:** Python PDF parser giving character-level positions, making it the precise option for extracting tables and layout.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1

### PyMuPDF
**Short:** Fast Python PDF library extracting text, layout and embedded images; the usual parser feeding a RAG corpus.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, applied-ml/vision-speech-and-multimodal @3

### pyserini
**Short:** Python IR toolkit wrapping Anserini/Lucene BM25 and FAISS dense retrieval for reproducible hybrid search.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/ann-index-library @2, ml-lifecycle/evaluation-and-benchmarks @3

### QuestionAnswerAdvisor
**Short:** Spring AI advisor that retrieves from a vector store and injects the context into the prompt; naive RAG in one bean.
**Kind:** api
**Lang:** java
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/prompting-context-and-structured-output @2

### Qwen3-Embedding
**Short:** Alibaba's open-weight embedding family (0.6B/4B/8B) with 32k context and Matryoshka dims; current MTEB leader.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/nlp-and-text @3

### RAG stacks
**Short:** The assembled retrieval-augmented generation stack used for grounded QA over a corpus that keeps changing.
**Kind:** concept
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1

### rank-bm25
**Short:** Pure-Python BM25 implementation for prototyping lexical retrieval over a small in-memory corpus; not production scale.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/rag-and-document-processing @3

### rank_bm25
**Short:** Small pure-Python BM25 implementation for in-memory sparse keyword retrieval over a document collection.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/lexical-and-hybrid-search @1

### RetrievalAugmentationAdvisor
**Short:** Spring AI advisor that retrieves from a VectorStore and injects the context into the prompt for naive or advanced RAG.
**Kind:** api
**Lang:** java
**Roles:** search-retrieval/rag-and-document-processing @1

### ScaNN
**Short:** Google's approximate nearest-neighbour library, tuned for the best recall-per-QPS at billion-vector scale.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/ann-index-library @1

### Self-RAG GitHub
**Short:** The original Self-RAG paper code and Llama-2 7B/13B fine-tunes that emit retrieve/critique reflection tokens.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, model-training/fine-tuning-and-peft @3

### sentence-transformers
**Short:** Library for sentence/document embeddings and cross-encoder rerankers, with pooling, training and evaluation built in.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/ann-index-library @1, applied-ml/nlp-and-text @2, search-retrieval/reranking @3, search-retrieval/rag-and-document-processing @3

### sentence-transformers CrossEncoder
**Short:** sentence-transformers class that scores a query and document jointly for second-stage reranking of retrieved candidates.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/reranking @1, applied-ml/nlp-and-text @3

### SentenceTransformers
**Short:** Python library for sentence/text embedding models and cross-encoders: encode, fine-tune and score similarity.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/ann-index-library @1, applied-ml/nlp-and-text @2, model-training/fine-tuning-and-peft @2, search-retrieval/reranking @3

### Solr
**Short:** Apache Lucene-based search server: inverted-index full-text search, faceting and distributed SolrCloud sharding.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/document @3

### SPLADE
**Short:** Learned sparse retrieval model that expands a query/document into weighted vocabulary terms usable by an inverted index.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/reranking @3, search-retrieval/ann-index-library @3

### Tavily Search API
**Short:** RAG-oriented web search API that returns cleaned, LLM-ready page text instead of raw HTML result links.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/tool-use-and-mcp @2, search-retrieval/lexical-and-hybrid-search @3

### Thomson Reuters AI
**Short:** Legal research assistant built into Westlaw and Practical Law, answering over licensed case law with citations.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1

### Typesense
**Short:** Open-source typo-tolerant search engine with an inverted index plus vector search, tuned for instant-search UIs.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/vector-store @2

### Unstructured.io
**Short:** Document ingestion library that parses PDFs, Office files and HTML into structure-aware elements for chunking.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1

### Vector index
**Short:** Generic term for the structure that makes embedding similarity search fast, from brute-force flat to HNSW or IVF.
**Kind:** concept
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, data-stores/vector-store @3

### Voyage AI
**Short:** Managed embedding provider whose voyage-4 family includes domain models for code, finance and legal text.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, search-retrieval/reranking @3
