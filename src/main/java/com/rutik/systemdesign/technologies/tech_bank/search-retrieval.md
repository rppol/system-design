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

A domain is a managed cluster: you choose data-node instance types and counts, optional dedicated master nodes and storage tiers, and AWS handles patching, snapshots to S3 and blue/green deployments for configuration changes. Access control is layered unusually, with an IAM resource policy over the whole cluster and optional fine-grained control mapping roles down to individual indices, documents and fields. The `k-NN` plugin adds vector fields backed by HNSW graphs, so one domain answers BM25, vector and filtered hybrid queries.

Reach for it when you are already on AWS and want search or log analytics without running nodes yourself. The costs are that capacity is provisioned and billed by the hour whether or not you query it, engine versions trail upstream OpenSearch, and shard sizing mistakes are still entirely yours to make. A serverless collection removes the sizing decision at a different price shape, and self-managed OpenSearch stays cheaper if you have the operational appetite.

### ANN retrieval
**Short:** Approximate nearest-neighbour search over embeddings, served by libraries like FAISS or managed vector databases.
**Kind:** concept
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, data-stores/vector-store @2

Exact nearest-neighbour search compares the query against every stored vector, which is linear in corpus size. Approximate methods accept a small, measurable recall loss in exchange for visiting only a fraction of the space: a navigable graph walked greedily as in HNSW, inverted lists over learned centroids as in IVF, random-projection trees, or hashing, usually combined with quantization that compresses each vector so more of the index fits in memory. Every method exposes one knob that trades recall for latency at query time, such as `efSearch` or `nprobe`.

Reach for it once the corpus passes roughly a hundred thousand vectors or the latency budget is tight. An approximate index degrades silently, returning plausible neighbours that are not the true nearest, so recall@k must be checked against brute force on real queries after every tuning change. Below that scale a flat exact index is simpler, always correct, and fast enough.

### Annoy
**Short:** Spotify's tree-based approximate nearest-neighbour library; memory-mapped, read-only after build.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/ann-index-library @1, applied-ml/recommenders-and-graph-ml @3

Annoy builds a forest of random-projection trees; you trade recall against latency by adding trees at build time and inspecting more nodes at query time. Its distinguishing property is the file format: the built index is memory-mapped, so several processes on a machine share one copy in page cache and a worker starts serving immediately without loading gigabytes into its own heap.

The cost of that design is immutability — adding vectors means rebuilding and swapping the file — so it suits a corpus refreshed by a batch job rather than one updated continuously. It has no GPU support and generally sits below HNSW on the recall-versus-latency curve, so choose it for operational simplicity and shared memory across read-heavy replicas, not for raw speed.

### Anserini
**Short:** Lucene-based toolkit for reproducible sparse retrieval baselines (BM25, SPLADE) on IR benchmark collections.
**Kind:** tech
**Lang:** java
**Roles:** search-retrieval/lexical-and-hybrid-search @1, ml-lifecycle/evaluation-and-benchmarks @3

It wraps Lucene behind command-line indexers and searchers built around the standard IR file formats, so a document collection, a topics file and a qrels file go in and a run file comes out ready for `trec_eval`. Beyond plain BM25 it covers pseudo-relevance feedback such as RM3 and impact-scored indexes for learned sparse models, and its regression harness pins the expected effectiveness numbers for known collections, so a change that quietly degrades retrieval fails a test instead of being discovered in someone's paper.

Reach for it when you need a defensible lexical baseline, which is the number every dense retriever must beat before it is worth deploying, or when reproducing published results. It is a JVM research toolkit rather than a serving system; from Python, pyserini exposes the same indexes with less ceremony, and production lexical search belongs in Elasticsearch, OpenSearch or Solr.

### BAAI/bge
**Short:** BAAI's open BGE text-embedding family: strong open-source English retrieval embeddings in several sizes.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/nlp-and-text @3

The family spans small, base and large English and Chinese encoders plus the multilingual `bge-m3`, all trained with large-scale contrastive learning and released under permissive licences, so the same recipe yields a 384-dimension model cheap enough for CPU and a 1024-dimension one when recall matters more than cost. The English retrieval models expect a short instruction prefix on the query and none on the passage; forgetting it is the usual cause of disappointing recall, because query and passage then land in slightly different regions of the space with nothing to indicate anything went wrong.

Reach for it when you want strong open-weight retrieval embeddings you can run yourself, avoiding a per-token bill and keeping documents inside your own network. Hosted providers still lead on very long inputs and on domain-specialised variants, and the matching `bge-reranker` cross-encoder is what closes most of the remaining quality gap on the top-k, rather than reaching for a bigger embedding model.

### Bedrock Knowledge Base
**Short:** AWS Bedrock managed RAG: ingests S3 documents, chunks and embeds them, and answers queries with citations.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/vector-store @3, platform-delivery/cloud-platform-and-cost @3

You point it at an S3 prefix or another supported source, choose a chunking strategy and an embedding model, and pick a vector store such as OpenSearch Serverless, Aurora PostgreSQL with pgvector, or a third-party database, and the ingestion job parses, chunks, embeds and writes the vectors, re-syncing when the source changes. At query time `Retrieve` returns matching chunks while `RetrieveAndGenerate` runs the whole retrieve-then-answer loop against a Bedrock model and returns citations pointing back at the source documents.

Reach for it when the corpus already lives in S3, the team is on AWS, and the value is in shipping grounded answers rather than in owning a retrieval pipeline. What you give up is control of exactly the parts that decide RAG quality: chunk boundaries, query rewriting, hybrid fusion and reranking are configuration rather than code. Once you are tuning those seriously, an explicit pipeline over your own store is the more honest place to be.

### BGE
**Short:** BAAI's open text-embedding family for dense and hybrid retrieval, with matching cross-encoder reranker models.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, search-retrieval/reranking @3

These are bidirectional encoders fine-tuned in stages, with masked pretraining, then weakly supervised contrastive training on mined pairs, then supervised training with hard negatives, which is what puts them ahead of a general-purpose sentence encoder on retrieval specifically. Sizes run from a 384-dimension small model to 1024-dimension large ones, and `bge-m3` adds multilingual coverage, an 8192-token window and the unusual property of emitting dense, sparse and multi-vector representations from a single pass, so one model can feed all three legs of a hybrid retriever.

Reach for them when self-hosting is the point, whether for cost at volume or because documents cannot leave your network; an ordinary GPU embeds millions of chunks in hours. Remember that changing embedding model invalidates every stored vector, so the corpus must be re-embedded in full and the index rebuilt, and pair the retriever with the family's cross-encoder reranker before reaching for a larger encoder.

### BGE-reranker
**Short:** Open-weight cross-encoder from BAAI that rescores retrieved passages against the query; free to self-host.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

A cross-encoder concatenates the query and one candidate passage into a single input and runs the transformer over both together, so attention crosses between them and the model judges whether this passage answers this query, rather than comparing two independently computed vectors. The output is one relevance score per pair, which means cost is linear in the number of candidates and nothing can be precomputed: you rerank the top twenty to a hundred results from a first-stage retriever, never the corpus.

Reach for it when retrieval brings back roughly the right region of the corpus but the ordering is wrong, which is the common failure and the one that most damages a generated answer, since the model leans on whichever chunks appear first. Budget for the latency, because a GPU pass over a hundred pairs is tens of milliseconds and CPU is far worse. A hosted reranking API removes the operational work at a per-call price.

### BGE-reranker-large
**Short:** BAAI open cross-encoder reranker rescoring query-document pairs; 512-token limit, best open-source quality.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

It is the largest of the original BGE cross-encoders, built on a multilingual RoBERTa-class backbone, and it scores a concatenated query and passage in one forward pass rather than comparing precomputed vectors. The 512-token input limit is the constraint that shapes how you use it: query and passage must fit together, so chunks over roughly four hundred tokens are truncated and the tail of a long passage is never seen by the scorer, which quietly caps the quality it can deliver on long chunks no matter how good the model is.

Reach for it when you want strong open-weight rerank quality and can afford a GPU pass over the candidate list. If latency or cost dominates, the base and MiniLM-class rerankers give most of the gain far more cheaply, and if chunks are long or the corpus is multilingual, the v2 generation with a wider window is the better starting point.

### BGE-reranker-v2-m3
**Short:** Open multilingual cross-encoder that rescores retrieved passages against the query in a second stage.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

It is the M3-generation reranker, built on the same multilingual backbone as `bge-m3`, so it scores query-passage pairs across many languages and handles cross-lingual pairs where question and passage are not in the same language, a case that defeats rerankers trained only on English data. Like every cross-encoder it takes the pair as one input and returns a single logit, so scores order one candidate list correctly but are not calibrated probabilities and should not be compared across queries or cut at a fixed threshold without normalising.

Reach for it as the default open-weight reranker for a multilingual or non-English corpus, sitting after a hybrid first stage over a few dozen candidates. Its cost is a transformer pass per candidate, which is why that list stays short. Where only English matters, a MiniLM-class cross-encoder is a fraction of the compute for a comparable result.

### Casetext CoCounsel
**Short:** Legal AI assistant doing retrieval-augmented research and document review over Westlaw case law.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @3

As publicly described it is organised around discrete skills rather than a chat box: search case law, summarise a document, review a contract against a policy, prepare a deposition outline. Each skill is a fixed pipeline that retrieves from licensed legal content, extracts the relevant passages and drafts an answer with citations back to the source. Grounding every claim in a licensed corpus rather than in the model's memory is the whole product argument, because in this domain a fabricated citation is a professional-conduct problem rather than merely a wrong answer.

It is worth studying as a vertical RAG product whose moat is the licensed corpus and the expert evaluation rather than the model; it became part of Thomson Reuters. The transferable lesson for any regulated domain is that narrow task-shaped skills with verifiable citations beat an open-ended assistant, and a human still reviews the output before it is used.

### chroma-haystack
**Short:** Haystack integration package wiring ChromaDB in as the document and embedding store for a RAG pipeline.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/vector-store @2

The package supplies `ChromaDocumentStore` plus the retriever components that pair with it, so a Haystack pipeline writes documents and embeddings into Chroma and queries them back by embedding similarity or by text, with metadata filters translated into Chroma's own `where` clauses. Chroma's persistent in-process mode means the store is a directory on disk rather than a service, so a full retrieval pipeline runs end to end on a laptop with nothing else deployed and no container to start.

Reach for it for prototypes, demos and small single-node applications where you want a real document store without operating one. It inherits Chroma's ceiling: one process owns the data, scaling is vertical, and there is no replication, so a production workload moves to the OpenSearch, pgvector, Qdrant or Weaviate integration instead. Because the store sits behind Haystack's interface, that swap is a component substitution rather than a rewrite.

### Cohere
**Short:** Hosted model provider whose Rerank API rescoring a candidate list is the common second stage in RAG.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/reranking @1, applied-ml/nlp-and-text @3

Cohere sells enterprise-oriented models through an API and, unusually, also publishes open weights for several of its Command models under a non-commercial licence, which makes private and VPC deployment a real conversation for customers who cannot send text to a vendor. The retrieval line is the part most stacks touch: Embed for dense vectors with explicit input types, Rerank as a hosted cross-encoder over a candidate list, and Command models trained to answer from supplied documents with inline citations.

Reach for it when retrieval quality and deployment location matter more than having the strongest general-purpose chat model. For frontier reasoning and broad multimodality the larger labs are ahead. For a rerank stage specifically, the hosted endpoint is usually the cheapest quality improvement available to a RAG pipeline, and the self-hosted alternative is a BGE or Jina cross-encoder on a GPU you already run.

### Cohere Embed API
**Short:** Cohere's managed embedding endpoint (embed-v4.0): multimodal inputs, 128k context and truncatable Matryoshka dims.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/nlp-and-text @3

One call takes a list of inputs plus an `input_type` telling the model whether these are queries or documents, and returns vectors in the requested `embedding_types`, which can be full-precision floats or quantised `int8`, `uint8` or binary. Matryoshka training is what makes the dimension a choice rather than a property of the model: the leading coordinates carry the most information, so truncating a vector to a smaller width degrades gracefully instead of destroying it, and you trade index size against recall without retraining or re-embedding.

Reach for it when index memory is a real cost, since binary vectors with a float rescoring pass over the top candidates is the standard way to shrink a large index while keeping quality. Batch your calls, respect the per-request token limits, and embed queries and documents with matching settings or the scores mean nothing. Self-hosted BGE, E5 or Qwen embeddings remain the alternative when data cannot leave the network.

### Cohere Rerank
**Short:** Managed cross-encoder reranking API that rescores retrieved documents against the query before generation.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

The call takes a query, a list of candidate documents and a `top_n`, and returns those candidates reordered with a relevance score each, computed by a cross-encoder that reads query and document together instead of comparing precomputed vectors. Nothing is stored between calls, so it slots in behind whatever first stage already exists, whether BM25, dense or a fusion of both, without touching your index or your ingestion pipeline.

Reach for it when the right passage is somewhere in the top fifty but not the top three, which is the ordinary state of a freshly built RAG pipeline and the cheapest quality win available to it. Cost scales with candidates multiplied by document length, so cap the list and truncate long documents rather than sending everything, and remember the network round trip lands in the middle of your request path. Self-hosting a BGE or MiniLM cross-encoder is the alternative when pricing or data residency rules the API out.

### Cohere Rerank 3.5
**Short:** Managed cross-encoder reranker that rescores retrieved passages; 100+ languages and a 4096-token context.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

It reads query and candidate together in a single pass, so the score reflects term-level interaction that first-stage vectors cannot represent, and the wider context window means a long chunk is judged whole rather than on its opening few hundred tokens. Documents may also be sent as structured fields rather than flat text, which lets a title, a body and metadata each contribute in their own right instead of being concatenated and then truncated at an arbitrary point.

Reach for it when the corpus is multilingual, the chunks are long, or the search is over semi-structured records such as products or support tickets where the deciding signal is not in the prose. Latency and price both scale with the number and length of candidates, so rerank a few dozen rather than a few hundred. If none of those conditions apply, a small self-hosted cross-encoder captures most of the improvement for the price of hardware you already have.

### ColBERT
**Short:** Late-interaction retrieval model scoring per-token embeddings with MaxSim: near cross-encoder quality, lower latency.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1, search-retrieval/lexical-and-hybrid-search @2, search-retrieval/ann-index-library @3, applied-ml/nlp-and-text @3

Instead of collapsing a passage into one vector, it keeps an embedding per token and scores a pair with MaxSim: for each query token take the highest similarity against any passage token, then sum those maxima. That preserves the term-level matching a single vector destroys while still allowing passage embeddings to be precomputed and indexed offline, which is why it sits between a bi-encoder and a cross-encoder on both quality and cost. The v2 line adds residual compression so each token vector costs a couple of bytes.

Reach for it when single-vector retrieval keeps missing exact entities and rare terms and a cross-encoder is too slow to run over enough candidates. The price is index size and machinery: many vectors per passage, a specialised engine such as PLAID or a store with native multi-vector support. Where the corpus is small or a reranker is affordable, the simpler stack usually wins.

### cross-encoder/ms-marco-MiniLM-L-6-v2
**Short:** Small 6-layer cross-encoder trained on MS MARCO; a fast self-hosted relevance reranker for retrieved passages.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

It is a six-layer distilled BERT fine-tuned on MS MARCO relevance pairs and published for `sentence-transformers`, scoring a query and passage read together and returning a single logit where higher means more relevant. The scale is arbitrary, so the numbers order one candidate list and mean nothing in isolation. Being six layers is the entire point: it scores a batch of candidate pairs in milliseconds on a GPU and stays usable on CPU, which is what makes reranking affordable inside a request path at all.

Reach for it as the default self-hosted reranker for English text when a hosted API is unwanted. Two limits decide whether it helps: the 512-token window must hold query and passage together, so long chunks are truncated, and it inherits MS MARCO's short web-question flavour, which transfers poorly to legal, medical or code corpora. Test on your own queries, and move to a larger BGE or Jina cross-encoder where the extra latency is affordable.

### deepset Cloud
**Short:** Hosted deployment and evaluation surface for Haystack RAG pipelines.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, platform-delivery/cloud-platform-and-cost @3

It is the commercial platform around Haystack: pipelines are defined with the same components and YAML the open-source framework uses, then deployed as managed endpoints with model and store connections handled for you. The surface that justifies it is evaluation, with labelled question sets, ground-truth answers and side-by-side comparison of pipeline variants, so a change to the chunker or the retriever is judged by numbers on a held-out set rather than by trying three prompts and forming an impression.

Reach for it when a team needs a shared, governed place to iterate on retrieval quality and cannot staff the platform work itself. Because the pipelines are portable Haystack definitions, the lock-in is mostly operational rather than structural. If the pipeline is already stable and only needs hosting, deploying the open-source framework on your own infrastructure is considerably cheaper.

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

The prefixes are part of the training, not a convention: every query is embedded as `query: …` and every passage as `passage: …`, because the model was trained contrastively on asymmetric pairs and the prefix tells it which side it is encoding. Drop them, or apply the same one to both sides, and retrieval quality falls sharply while nothing visibly breaks, which makes it the most common way these models are misused. The family runs small through large, with multilingual variants and later instruction-tuned versions built on bigger backbones.

Reach for it as a strong, permissively licensed self-hosted baseline, particularly the small and base sizes, which embed a large corpus quickly on modest hardware. Check the prefix convention against the exact checkpoint you loaded rather than assuming, and re-embed everything if you change checkpoint. Where inputs are long or heavily multilingual, `bge-m3` or a long-context hosted model is the better fit.

### Elastic Cloud
**Short:** Elastic's managed hosting for Elasticsearch and Kibana: provisioned clusters with upgrades and scaling handled.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, platform-delivery/cloud-platform-and-cost @3, observability/logging @3

You pick a region and a deployment template and get a cluster with data tiers, dedicated master nodes, Kibana and the commercial plugins wired together; scaling is an edit to the deployment that performs a rolling change, and searchable snapshots let frozen data live in object storage while remaining queryable. It is also where Elastic's licensed features land first, including the newer query language, machine-learning jobs and the built-in inference and semantic-text tooling, because the company operates it itself.

Reach for it when you want Elastic's own stack rather than the fork, and want upgrades, snapshots and monitoring to be someone else's job. The cost is the usual managed premium plus data transfer, and cluster internals remain entirely your problem, because a badly sharded index is exactly as slow on managed hardware. Amazon OpenSearch Service is the equivalent for the forked lineage, and self-hosting is cheaper if you have the operational capacity.

### Elasticsearch
**Short:** Distributed inverted-index search engine for full-text/BM25, hybrid dense+sparse retrieval, and log analytics.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/document @2, observability/logging @2, data-stores/vector-store @3, data-stores/warehouse-and-olap @3

Elasticsearch wraps Lucene in a distributed layer: documents are analyzed into an inverted index, indices are split into shards spread across nodes and replicated, and a query scatters to the shards and gathers the merged top results, so both corpus size and query throughput grow by adding nodes. Relevance defaults to BM25 and is tunable per field through analyzers, boosts and function scoring, and the same engine also serves aggregations, dense-vector nearest-neighbour search and learned sparse retrieval — which is why one cluster can back both log analytics and hybrid retrieval with rank fusion.

Reach for it when you need real relevance ranking, filters and facets over text, rather than a wildcard `LIKE` scan in your relational database. Do not treat it as a system of record: there are no transactions across documents, refresh is near-real-time rather than immediate, and the primary shard count is fixed when the index is created, so capacity planning happens up front.

### Elasticsearch/OpenSearch
**Short:** Distributed inverted-index search engine and JSON document store, widely used as the log-aggregation backend.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, observability/logging @2, data-stores/document @2, data-stores/vector-store @3

The two engines share an ancestor and most of their surface, including the Lucene core, the shard and replica model and largely the same query DSL, which is why documentation, client patterns and operational instincts transfer between them. They diverged when Elastic changed licensing and AWS forked the last Apache-licensed release, and the gap has widened since: vector search, security defaults, the analytics plugins and the query languages each evolved separately, so a mapping, a plugin or a client from one is no longer guaranteed to work against the other.

Treating them as one technology is fine for architecture and for reasoning about indexing, relevance, sharding and capacity. It stops being fine the moment you write configuration or pin a client library, where you must commit to a lineage and read its own documentation. Pick by ecosystem: Elastic's licensed features and Elastic Cloud on one side, the Apache-licensed fork and AWS's managed service on the other.

### Embedding model
**Short:** A text encoder producing fixed-length vectors for similarity search, e.g. all-MiniLM-L6-v2 at 384 dims on CPU.
**Kind:** concept
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, applied-ml/nlp-and-text @2

An encoder maps text to a fixed-length vector such that semantically similar text lands nearby under cosine similarity, which turns retrieval into a geometry problem an index can solve quickly. Two properties decide how one behaves: the maximum sequence length, past which input is silently truncated rather than rejected, and whether it was trained symmetrically or asymmetrically, for matching a short query against a long passage. The asymmetric ones generally expect a prefix or instruction marking which side is being encoded.

Choose one by measuring recall on your own queries rather than by leaderboard position, since public benchmarks rarely resemble a specific domain. Dimension is the ongoing cost, because it multiplies through every stored vector and every distance computation, and the switching cost is total, since a new model means re-embedding the whole corpus. Where exact identifiers and rare terms matter, pair it with a lexical retriever.

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

In FAISS the index type is the entire design decision. `IndexFlat` is exact brute force and the correctness baseline; `IVF` partitions the space into cells and searches only `nprobe` of them; `HNSW` walks a navigable small-world graph; and product quantization (`PQ`, `IVFPQ`) compresses each vector into a handful of bytes so a billion of them fit in memory, paying for it in recall. IVF and PQ indexes must be trained on a representative sample before vectors are added, and the GPU implementations make both building and searching enormously faster.

It is a library, not a service, and the gap is the point: no metadata filtering, no updates in place beyond add and remove-by-id, no replication, no query language — just an index you can write to a file and load again. That is what a vector database wraps and operates for you. Reach for FAISS directly for offline retrieval, for benchmarking recall against exact search, and for embedding a searchable index inside a process.

### FlashRank
**Short:** Tiny, fast cross-encoder reranking library for resource-constrained RAG deployments.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/reranking @1

It ships small quantised cross-encoders that run through ONNX Runtime on CPU, so reranking becomes a `pip install` and a few hundred megabytes rather than a GPU and a serving stack. The default models sit in the MiniLM and tiny-transformer size class, with larger options when quality matters more than milliseconds, and the API is one object and one rerank call over a list of passages.

Reach for it when a hosted rerank API is ruled out by cost, latency or data residency and there is no GPU in the deployment, such as a serverless function or an edge box. The tradeoff is quality, since the smallest models are clearly weaker than a full cross-encoder and CPU throughput limits how many candidates you can afford, so keep the list short. Where a GPU exists, a BGE or Jina reranker is a better use of it.

### Gemini Embedding API
**Short:** Google's hosted multimodal embedding endpoint placing text, image, audio and PDF into one vector space.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/vision-speech-and-multimodal @3

### Harvey AI
**Short:** Vertical legal AI product for contract review, legal research and drafting over a firm's document corpus.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @3

The product is shaped around law-firm workflows rather than being a general assistant: uploading a matter's documents, asking questions across them with citations, extracting and comparing clauses over a set of contracts, and drafting from a firm's own precedent. The engineering that makes that viable is unglamorous, being document parsing that survives scanned exhibits and tables, chunking that respects clause and section boundaries, retrieval confined to one matter's permission boundary, and evaluation by domain experts rather than by a public benchmark.

As a reference point it shows what a vertical RAG product must get right: strict tenancy and confidentiality, provenance on every claim, and a review step that assumes the output will be checked before use. Building the same thing in-house means owning all of that plus the corpus and the expert review time. For anything less than a regulated, document-heavy workflow, a general RAG framework over your own store is the sensible starting point.

### Haystack
**Short:** deepset's pipeline framework for production RAG and NLP: composable retrievers, readers, generators and agents.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @2, applied-ml/nlp-and-text @3

A pipeline is an explicit graph of components - converter, splitter, embedder, retriever, prompt builder, generator, ranker - wired by named inputs and outputs, so a RAG application is a declarative structure you can serialize to YAML, diff, evaluate and serve rather than a chain of ad-hoc calls. Branching, looping and tool-calling agents are expressed in the same graph, and integrations cover the usual document stores and model providers.

Reach for it when a retrieval application has enough moving parts that you want the wiring to be inspectable and swappable - trying a different retriever or reranker becomes a component substitution. For a single retrieve-then-generate call the framework is more structure than the problem needs.

### haystack-ai
**Short:** Deepset's pipeline framework for composing retrievers, rankers, prompt builders and generators into RAG apps.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @2, llm-apps/prompting-context-and-structured-output @3

This is the distribution name of the 2.x line, and the rename marks a real break rather than a version bump: the older package had node classes and predefined pipeline shapes, while this one is a general component graph where you declare inputs and outputs and connect them yourself, so the two are not drop-in compatible. The core package deliberately stays small, carrying the pipeline engine, an in-memory document store and the common components, while every vector store and model provider arrives as a separate integration package.

Install it when starting new work, and treat any tutorial written against the older package as a different framework. The split packaging keeps dependencies light but means a failure is usually a missing integration rather than a bug. Pipelines serialise to YAML, which is what makes them deployable and reviewable, and is also the quickest way to notice that a pipeline has grown too large to reason about.

### Hnswlib
**Short:** Header-only HNSW approximate-nearest-neighbour index library; best recall per query, entire index held in RAM.
**Kind:** tech
**Lang:** python, cpp
**Roles:** search-retrieval/ann-index-library @1

It is a header-only C++ implementation of HNSW with Python bindings, and it stays deliberately small: build an index, add vectors, query it, save and load it. The graph is layered — search starts at a sparse top layer and descends greedily to progressively denser ones — with `M` setting graph degree and memory, `ef_construction` setting build quality, and `ef` traded at query time between recall and latency.

The entire index sits in RAM, which is where its speed and its limit both come from; there is no metadata filtering, no sharding, and no server. Reach for it when you want the best recall-per-millisecond inside your own process and the corpus fits on one machine — a research baseline, an embedded index, a sidecar. Once you need payload filters, multi-tenancy, or durability, that is a vector database's job.

### Jina Embeddings
**Short:** Open-weight embedding model family with long context windows and multilingual variants for retrieval.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, applied-ml/nlp-and-text @3

The family's calling card is long input: the models handle windows far past the 512 tokens typical of BERT-derived encoders, so a whole document or a large chunk embeds as one vector instead of being split and averaged. Later versions add task-specific adapters selected at call time for retrieval, clustering or classification, and Matryoshka dimensions so a vector can be truncated to shrink an index without re-embedding. Weights are published openly alongside a hosted endpoint, so the same checkpoint runs either way.

Reach for it when chunks are long, or when you want the freedom to move between self-hosting and an API without changing the vector space. Long context is not free: attention cost grows with length, and one vector over a very long passage blurs detail, so smaller chunks still retrieve more precisely for lookup-shaped questions. Keep the task setting identical between indexing and querying or the scores will not compare.

### Jina Reranker
**Short:** Jina's cross-encoder reranking models, available hosted or open-weight, including multilingual and image+text variants.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1, applied-ml/vision-speech-and-multimodal @3

These are cross-encoders that read query and candidate together, published in several shapes: small English models for cheap CPU reranking, multilingual ones, long-context variants that score a full document rather than a truncated head, and a multimodal version that ranks images against a text query. A later generation takes a listwise approach driven by a generative model, reading several candidates at once and ordering them jointly rather than scoring each in isolation, which captures relative comparisons a pointwise scorer cannot.

Reach for one when retrieval returns plausible but badly ordered candidates and you want the fix self-hosted, since open weights mean the same model runs in a notebook and in production. The economics are the standard reranker economics, with cost linear in candidates, so keep the list to a few dozen and truncate long documents unless you deliberately chose a long-context variant. A hosted rerank API is simpler where per-call pricing is acceptable.

### JPMorgan COiN
**Short:** JPMorgan's internal contract-intelligence system that extracts clauses and terms from loan agreements.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, applied-ml/nlp-and-text @2

As publicly described it is a document-understanding system rather than a search product: credit agreements are parsed, clauses are located and classified, and specific attributes such as parties, dates, covenants and collateral terms are extracted into structured fields that downstream systems can query, with models trained on the institution's own historical agreements and their human-reviewed annotations. Because these documents are long, repetitive and highly templated within a product line, per-clause classification works where a general-purpose extractor would not.

It is cited as the canonical example of clause extraction at scale replacing manual review in a regulated setting, where the win is consistency and turnaround rather than the removal of lawyers, since extractions are reviewed and uncertain clauses are routed to a human by confidence threshold. The pattern generalises to any high-volume contract corpus, and the hard part is always assembling the labelled data rather than choosing the model.

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

The 0.10 repackaging split the monolith apart: this package holds the abstractions, including `Document` and `Node`, node parsers, indices, retrievers, query engines, response synthesizers and the settings and callback machinery, while every model provider, vector store and reader ships as its own `llama-index-*` package installed on demand. Installing the umbrella `llama-index` package pulls a default OpenAI-flavoured set on top of it, which is why a minimal deployment depends on core plus exactly the integrations it actually uses.

Depend on core directly when building a library or a container where dependency weight and version conflicts matter, and add integrations explicitly. The tradeoff is that import paths moved, so code written against pre-0.10 examples needs rewriting, and a missing integration surfaces as an import error rather than a helpful message. For a throwaway prototype the umbrella package is still the shorter path.

### llama-index-embeddings-openai
**Short:** LlamaIndex integration package that calls OpenAI embedding models to vectorize nodes and queries.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2

It supplies `OpenAIEmbedding`, the adapter satisfying the framework's embedding interface by calling the provider's endpoint, and the framework then uses it in two distinct places: batching node text during ingestion, and embedding the query at retrieval time. Both must use the same model, which is why the setting normally lives in the global `Settings` object rather than being passed per call. Mixing models between index and query yields vectors that compare meaninglessly, so retrieval goes quietly wrong instead of failing.

Install it when the embedding provider is OpenAI; every other provider is a sibling package with the same shape, so switching means changing one object. Watch the operational edges, because batch size and rate limits govern how fast a large corpus ingests, embedding cost is paid per token across the whole corpus at build time, and re-embedding is mandatory if the model changes. A self-hosted `HuggingFaceEmbedding` avoids both the bill and the data leaving your network.

### llama-index-llms-openai
**Short:** LlamaIndex integration package binding OpenAI models into its query and agent pipelines, one package per provider.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/llm-gateway-and-routing @2

It provides the `OpenAI` LLM class implementing the framework's completion, chat, streaming and async interfaces plus native function calling, which is what lets the same query engine or agent run against a different provider by swapping one object. The per-provider packaging is deliberate, since each vendor SDK is a heavy dependency with its own version constraints and the framework refuses to drag all of them into every install.

Install the one your deployment actually calls, and set it once through `Settings.llm` so response synthesis, query transformation and agent steps all use it instead of falling back to a default. Two things reliably bite: agent and structured-output paths assume native tool calling, so a provider without it degrades to prompt-based extraction, and context-window differences change how much retrieved text a response mode can compact into a single call. For a local model, the Ollama or OpenAI-compatible server packages are the equivalents.

### llama-index-postprocessor-cohere-rerank
**Short:** LlamaIndex node postprocessor that reorders retrieved chunks through the Cohere Rerank API before they hit the prompt.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/reranking @1, search-retrieval/rag-and-document-processing @2

A node postprocessor sits between the retriever and the response synthesizer, receiving the scored nodes and returning a modified list, and this one replaces vector-similarity ordering with cross-encoder relevance scores from the hosted API, keeping only `top_n`. The usual configuration is to widen the retriever deliberately, fetching thirty or fifty candidates instead of five, and let the reranker choose the handful that reach the prompt, which raises the chance the answer-bearing chunk is present while keeping the context small.

Reach for it when answers cite the wrong passage even though the right one was retrieved. The costs are a network call inside the query path and per-call pricing over the candidate list, so the widened `similarity_top_k` is what you are really paying for. Where data cannot leave the network, the local sibling that runs a `sentence-transformers` cross-encoder is the same component with a different model behind it.

### llama-index-retrievers-bm25
**Short:** LlamaIndex integration package providing an in-process BM25 retriever (via rank_bm25) for hybrid retrieval.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/rag-and-document-processing @2

It wraps the pure-Python `rank_bm25` implementation into a `BM25Retriever` built directly from a node list, so lexical retrieval needs no search server and no separate index: the retriever tokenises the nodes, holds the statistics in memory and scores every node per query. Combined with a vector retriever through a fusion retriever, it recovers exactly what embeddings lose, namely part numbers, error codes, function names and rare proper nouns, where literal token overlap is the entire signal.

Reach for it to add a lexical leg to a hybrid pipeline in a prototype or a small application. It inherits its backend's ceiling precisely, being a linear scan with no inverted index, all state in memory and rebuilt on every restart, so a corpus past a few tens of thousands of chunks, or one that must survive a restart, belongs in Elasticsearch, OpenSearch or a vector store with native BM25 and hybrid scoring.

### LlamaCloud
**Short:** LlamaIndex's managed ingestion and parsing service, handling tables, images and complex PDFs into a queryable index.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1, ml-lifecycle/ml-platform-and-pipelines @3

The piece that matters is parsing. LlamaParse runs layout-aware extraction over PDFs, slide decks and spreadsheets, optionally with a vision model, so a table comes back as a table and a multi-column page is read in reading order rather than interleaved, which is the failure that silently poisons a corpus long before anyone blames the retriever. Around it sits managed ingestion: connectors to sources, chunking, embedding and an index served behind a retrieval endpoint that a pipeline queries like any other retriever.

Reach for it when the corpus is real-world documents whose layout carries meaning, such as filings, manuals and contracts, and parsing rather than retrieval logic is the bottleneck. It is a paid hosted service that sees your documents, which settles the question for many organisations. Self-hosted alternatives are Unstructured, or `PyMuPDF` and `pdfplumber` plus your own chunking, cheaper but with the layout problems back in your hands.

### LlamaHub
**Short:** LlamaIndex's community registry of 150+ data connectors, vector-store adapters, LLM integrations and tool packs.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/tool-use-and-mcp @3

It is the catalogue of everything that plugs into LlamaIndex: readers pulling documents out of Notion, Slack, Google Drive, S3, databases and APIs, vector store and model integrations, agent tool specifications, and larger prepackaged pipelines. Each entry is published as its own installable package, so browsing the registry and installing a component are the same act rather than two separate steps with a copy-paste in between.

Reach for it to avoid writing a connector nobody should write twice, since getting documents out of a source system with pagination, authentication and metadata intact is tedious and already solved. Judge each entry on its own merits, though, because these are largely community contributions with uneven maintenance, and a loader that quietly drops attachments or metadata surfaces much later as inexplicably bad retrieval. For a critical source, read the loader before trusting it.

### LlamaIndex
**Short:** RAG-first framework: ingestion, chunking, indexes, query transforms and retrieval-driven agents over your data.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, llm-apps/agent-framework @2, llm-apps/prompting-context-and-structured-output @3, search-retrieval/ann-index-library @3

The pipeline is explicit and each stage is replaceable: readers pull documents from a source, node parsers split them into chunks with metadata, an index sits over the nodes, a retriever fetches candidates, and a query engine assembles them into a prompt with a response mode such as compacting everything into one call or summarizing hierarchically when the context will not fit. Its value is that the advanced retrieval patterns arrive as components rather than as papers you have to implement: sentence-window and auto-merging retrieval, recursive retrieval over document hierarchies, hypothetical-document and decomposition query transforms, and rerankers.

Reach for it when the hard part of your application is getting the right chunks in front of the model. When the hard part is agent control flow, tool orchestration and state across steps, an agent framework is the better spine and this becomes the retrieval tool it calls.

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

It is two components in sequence. PairRanker compares candidate outputs pairwise with a cross-attention model rather than scoring each in isolation, because judging which of two answers is better is a far easier learning problem than assigning an absolute quality score. GenFuser then takes the top-ranked candidates and generates a fused answer that can beat any single input. Candidates come from different models, so the ensemble exploits the fact that no one model wins on every prompt.

Reach for it in offline or batch settings where quality per output matters more than latency and cost, such as generating training data, building a preference dataset, or running a careful evaluation. It is impractical in a request path, since you pay for several generations plus a set of comparisons before answering anything. For online use, a router that picks one model per request, or a single reranking pass over a few samples, captures most of the benefit far more cheaply.

### Lucene
**Short:** The Java inverted-index library behind Elasticsearch and Solr: analyzers, postings lists, BM25 and HNSW vectors.
**Kind:** tech
**Lang:** java
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/ann-index-library @3

Lucene is a library, not a server. An analyzer chain turns text into terms, documents are written into immutable segments that background merges consolidate, and queries walk the postings lists scoring with BM25; it also indexes dense vectors with HNSW so lexical and vector search share one index and one query path.

Almost everyone meets it through Elasticsearch, OpenSearch or Solr, which are servers wrapped around it -- so its segment, merge and refresh behaviour is what explains their latency and disk usage. Reach for it directly only when embedding search inside a JVM application with no cluster.

### Meilisearch
**Short:** Lightweight search engine focused on instant typo-tolerant full-text results, with optional hybrid vector search.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/vector-store @3

It is built for search-as-you-type: a prefix index and bounded typo tolerance make partial words match, and results are ordered by an explicitly ranked list of rules covering words matched, typos, proximity, attribute importance and exactness, followed by your own sort criteria, instead of by a single opaque relevance score. That ordered rules list is the entire tuning model, and it is far easier to reason about than juggling BM25 field boosts, at the price of less control.

Reach for it for product catalogues, documentation search and application search where a single binary, a JSON document push and instant filtered results matter more than analytics or scale. The limits are the flip side of that: it holds working structures in memory and scales up rather than out, it is not a log store, and deep relevance engineering and aggregations belong to Elasticsearch or OpenSearch. Typesense occupies almost the same niche.

### Microsoft GraphRAG
**Short:** Microsoft's open-source graph RAG pipeline: entity extraction, Leiden communities and community summaries.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, data-stores/graph-db @3, applied-ml/recommenders-and-graph-ml @3

The pipeline is a configurable sequence rather than one call: chunking, entity and relationship extraction with prompts you can tune, graph construction and embedding, Leiden community detection at several resolution levels, and community-report generation. Prompt tuning matters more than usual, because the entity types the extraction prompt asks for determine what the graph can ever represent, and a generic prompt over a specialised corpus produces a graph of people and organisations when what you needed was genes, components or claim types.

Treat indexing cost as the deciding factor: an LLM call over every chunk and further calls per community make this a budgeted batch job rather than an ingestion script, and re-indexing after the corpus changes is not free. Reach for it when the questions are genuinely corpus-wide and the content is reasonably stable. Lighter alternatives, such as an LLM-extracted graph in Neo4j queried with Cypher, or plain hierarchical summarisation, cover many of the same needs for far less.

### ms-marco-MiniLM
**Short:** Small cross-encoder reranker trained on MS MARCO; rescores query-passage pairs after first-stage retrieval.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/reranking @1

The name describes a training set more than an architecture. MS MARCO is a large collection of real Bing queries paired with judged passages, and a MiniLM-sized transformer distilled from a larger model and fine-tuned on those pairs becomes a compact relevance scorer. Several checkpoints exist at different depths, from two layers to twelve, trading accuracy for latency along a smooth curve, and they are published for `sentence-transformers` so loading one and scoring a batch of pairs is a couple of lines.

Reach for one when reranking must cost close to nothing per query. The bias is worth naming: the training data is short, English and web-question shaped, so performance on long technical passages, non-English text or an enterprise vocabulary is noticeably worse than the benchmark numbers suggest. Test against your own queries, and if the gap matters, either fine-tune on in-domain pairs or move to a larger multilingual cross-encoder.

### nomic-embed-text
**Short:** Fully open-weight text embedding model with an 8192-token context, a practical self-hosted default for RAG.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, applied-ml/nlp-and-text @3

It is released with weights, training code and training data, which is rarer than open weights alone and matters when a model must be auditable or reproducible. Architecturally it replaces absolute positions with rotary embeddings and extends the window to 8192 tokens, so long chunks embed whole; like other asymmetric retrievers it expects a task prefix such as `search_query` or `search_document` on the input, and later versions add Matryoshka dimensions so a vector can be truncated to shrink the index.

Reach for it as a self-hosted default when a permissive licence and provenance matter, or when the deployment is offline, since it is small enough to run under Ollama or a CPU-only runtime. Hosted frontier embeddings still edge it on hard retrieval and on multilingual corpora. As always, the prefixes must match between indexing and querying, or recall drops with nothing in the logs to explain it.

### OpenAI Embeddings API
**Short:** OpenAI's hosted text embedding endpoint (text-embedding-3-small/large) producing vectors for semantic search and RAG.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, llm-apps/llm-gateway-and-routing @3

The endpoint takes a string or an array of strings plus the model and optionally `dimensions` and `encoding_format`, returning one vector per input in the order sent. Requesting `base64` rather than a JSON float array cuts response size substantially, which is noticeable when embedding a whole corpus. Inputs past the model's token limit are rejected rather than truncated, so ingestion code must count tokens and chunk beforehand, and both rate limits and cost are measured in tokens per minute rather than in requests.

The practical pattern for a bulk ingest is concurrent batched requests with retry on 429 and a persistent cache keyed by a hash of the text, so a re-run does not pay twice; the asynchronous batch interface is cheaper again when the corpus need not be embedded immediately. Keep query-time calls on a short timeout with a fallback path, because an embedding failure means no retrieval at all rather than a slightly degraded answer.

### OpenSearch
**Short:** Distributed search and analytics engine forked from Elasticsearch; BM25, log storage and a neural/hybrid search plugin.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, observability/logging @2, data-stores/vector-store @2

OpenSearch indexes documents into an inverted index and ranks with BM25, sharding both the corpus and the query load across nodes; the same cluster happily stores time-series logs, which is why it so often ends up being both the search engine and the log backend in one deployment. Its k-NN plugin adds vector fields and approximate nearest-neighbour search, so a single query can combine a lexical score with a vector score and filter on structured fields at the same time, instead of joining results from two systems.

Reach for it when you need lexical relevance, filters and aggregations alongside vectors. A dedicated vector database is simpler if lexical search is not part of the requirement.

### opensearch-haystack
**Short:** Haystack integration using OpenSearch as the document store, supporting BM25, kNN and hybrid retrieval.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, search-retrieval/lexical-and-hybrid-search @2

The integration provides `OpenSearchDocumentStore` and three retrievers over it, covering BM25, embedding similarity, and a hybrid path that runs both and fuses the rankings, so one store backs lexical and vector retrieval instead of a search cluster sitting beside a vector database. Documents keep arbitrary metadata and Haystack's filter syntax is translated into OpenSearch query DSL, which is what makes tenant, date and permission filters part of the same query rather than a post-filter that quietly ruins recall.

Reach for it when the deployment already runs OpenSearch, or when the corpus is large enough to need real sharding, replication and durability behind the pipeline. The price is operating a cluster, including mappings, `knn` index settings, shard sizing and enough memory for the HNSW graphs. For a prototype the in-memory or Chroma store is far less work, and the pipeline code does not change when you switch.

### pdfplumber
**Short:** Python PDF parser giving character-level positions, making it the precise option for extracting tables and layout.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1

It is built on `pdfminer.six` and exposes the page as geometry: every character with its font, size and bounding box, plus the lines and rectangles drawn on it. Table extraction follows from that primitive, since the default strategy infers a grid from ruling lines and a borderless table needs the text strategy or explicit column positions instead. Cropping a page to a region before extracting is how you reliably pull one repeated block out of a standard-format document.

Reach for it when the answer depends on layout, such as financial statements, invoices and forms where a number only means something in relation to its column heading. It reads the text layer only, so scanned documents need OCR first, and it is slow, being pure Python over character-level objects and easily an order of magnitude behind `PyMuPDF`, which is the better choice when you simply want the text of a large corpus quickly.

### PyMuPDF
**Short:** Fast Python PDF library extracting text, layout and embedded images; the usual parser feeding a RAG corpus.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, applied-ml/vision-speech-and-multimodal @3

It binds the MuPDF C library, which is where the speed comes from, and offers a page in several extraction shapes: plain text, a dictionary of blocks, lines and spans carrying coordinates and fonts, HTML preserving some structure, or a word list with bounding boxes. It also rasterises pages to images at a chosen resolution, which is the standard route into OCR or a vision model for scanned material, and it extracts embedded images, links and annotations.

Reach for it as the default parser for a document corpus of any size, since throughput decides whether re-ingesting is a coffee break or an overnight job. Two caveats decide adoption: reading order across a multi-column page needs the block coordinates rather than naive text extraction, and the AGPL licence means a closed-source commercial product needs the commercial licence. `pdfplumber` is slower but better for fine table work, and `pypdf` is the permissive lower-fidelity fallback.

### pyserini
**Short:** Python IR toolkit wrapping Anserini/Lucene BM25 and FAISS dense retrieval for reproducible hybrid search.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/ann-index-library @2, ml-lifecycle/evaluation-and-benchmarks @3

It wraps Anserini and Lucene for sparse retrieval — BM25, RM3 query expansion, and learned impact indexes such as SPLADE and uniCOIL — and FAISS for dense retrieval, behind one Python searcher API, with hybrid fusion of the two. Prebuilt indexes and the matching topics and qrels for standard collections like MS MARCO, BEIR, and TREC ship with it, so reproducing a published baseline is a few lines instead of a week of indexing.

Reach for it when the question is how good is my retrieval, not how do I serve it: measuring nDCG, MRR, and recall@k against real qrels before you commit to a production stack, or checking whether your embedding model actually beats BM25 on your domain. It is a research and evaluation harness, it needs a JVM for the Lucene half, and it is not built to be a serving system.

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

The family is built on the Qwen3 backbone and trained in stages, with large-scale weakly supervised contrastive training on synthetic pairs, supervised fine-tuning, and model merging, which is how the small 0.6B model stays competitive while the largest leads. Two properties matter operationally: instruction awareness, where prepending a task description to the query measurably changes the embedding and improves in-domain retrieval, and Matryoshka dimensions, so a vector can be truncated to fit an index budget without re-embedding anything.

Reach for it when you want frontier-level retrieval quality self-hosted, multilingual and including code. The 8B model is a real GPU commitment for both ingestion and query-time encoding, so most deployments run the 0.6B or 4B and spend the saved compute on a reranker instead. Keep the instruction string identical between indexing and querying, and remember that a leaderboard position is not a substitute for measuring recall on your own queries.

### RAG stacks
**Short:** The assembled retrieval-augmented generation stack used for grounded QA over a corpus that keeps changing.
**Kind:** concept
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1

The stack is a sequence of decisions and each one is a place quality leaks away: parsing documents into clean text, chunking so that a chunk is self-contained, embedding and indexing, retrieving with a lexical and a dense leg fused together, reranking the candidates, and generating an answer with citations. Retrieval failures dominate, because the model cannot answer from a chunk it never received, so the instrumentation that matters measures whether the answer-bearing chunk reached the context at all.

Reach for retrieval rather than fine-tuning when the knowledge changes, when provenance is required, or when access control must follow the user. The costs are an ingestion pipeline to keep in sync with the source, an index to operate, and an evaluation set you have to build yourself. For a small stable corpus that fits in a long context window, putting the documents in the prompt is simpler and often better.

### rank_bm25
**Short:** Small pure-Python BM25 implementation for in-memory sparse keyword retrieval over a document collection.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/lexical-and-hybrid-search @1

BM25 scores a document by summing, over the query terms it contains, an inverse-document-frequency weight multiplied by a saturating term-frequency factor. Two parameters control that shape: `k1` sets how quickly repeated occurrences of a term stop adding value, so a document mentioning a word twenty times does not outrank one mentioning it five times by four, and `b` sets how strongly the score is normalised by document length, with 1 meaning full normalisation and 0 none.

Reach for it to add lexical matching to a prototype or small hybrid retriever, where exact tokens such as identifiers, error strings and rare names are what embeddings blur. Tokenisation is entirely your responsibility, including lowercasing, stemming and stopwords. Scoring is a linear scan with no inverted index and no persistence, so anything real belongs in a search engine implementing the same formula over postings lists.

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

ScaNN's distinguishing idea is anisotropic vector quantization: it learns a quantization that penalises error along the direction which actually changes an inner product, rather than minimising plain reconstruction error, which buys higher recall at the same compression than ordinary product quantization. A search runs three tunable phases -- partition to select candidate regions, score them with the quantized approximation, then exactly re-rank a small top set -- so the recall-versus-latency curve is something you dial rather than accept. It is a library you build an index with in-process, aimed at maximum inner-product search over large, mostly static corpora. Reach for it when throughput at very large scale matters more than convenience; if you need metadata filtering, frequent updates, or a service rather than a library, FAISS or a vector database is the practical choice.

### Self-RAG GitHub
**Short:** The original Self-RAG paper code and Llama-2 7B/13B fine-tunes that emit retrieve/critique reflection tokens.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1, model-training/fine-tuning-and-peft @3

The repository holds the training and inference code plus the fine-tuned models from the Self-RAG paper. The idea is to teach a model to emit special reflection tokens during generation: whether retrieval is needed for this segment at all, whether a retrieved passage is relevant, whether the sentence just generated is actually supported by it, and how useful the overall response is. Critique therefore becomes part of decoding rather than a separate judging call, and the decoder can weigh candidate continuations by those token probabilities.

It is worth studying for the mechanism rather than the checkpoints: adaptive retrieval that skips the retriever for questions not needing it, and discards passages that do not help. Running it as published means adopting a fine-tuned base model, which is why most production systems reproduce the behaviour with prompted grader steps in a graph-structured pipeline instead of with specialised tokens.

### sentence-transformers
**Short:** Library for sentence/document embeddings and cross-encoder rerankers, with pooling, training and evaluation built in.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/ann-index-library @1, applied-ml/nlp-and-text @2, search-retrieval/reranking @3, search-retrieval/rag-and-document-processing @3

A raw transformer gives you per-token vectors; this library wraps encoder, pooling and normalization into one module so `model.encode(texts)` returns comparable fixed-size sentence vectors, batched, sorted by length and moved to GPU for you. That is the practical difference between having an embedding model and having embeddings.

The training half matters as much: contrastive losses such as MultipleNegativesRanking, evaluators for semantic-similarity and retrieval benchmarks, and a trainer - which is how you fine-tune an embedding model on your own query and positive-passage pairs and beat a general-purpose model on domain data. `CrossEncoder` is the complementary piece of a retrieval stack, scoring a query and document jointly for far better accuracy at far worse speed, so it reranks the top-k an ANN index returned rather than searching. Match the model's maximum sequence length to your chunk size: anything past it is truncated silently.

### sentence-transformers CrossEncoder
**Short:** sentence-transformers class that scores a query and document jointly for second-stage reranking of retrieved candidates.
**Kind:** api
**Lang:** python
**Roles:** search-retrieval/reranking @1, applied-ml/nlp-and-text @3

### Solr
**Short:** Apache Lucene-based search server: inverted-index full-text search, faceting and distributed SolrCloud sharding.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/document @3

Solr exposes Lucene over HTTP with a schema: fields are declared with types and analyzer chains up front, which catches mapping mistakes at index time rather than producing puzzling matches later. Its distinguishing strengths are faceting and grouping, including pivot facets, ranges and JSON facet aggregations, along with function queries and request-handler configuration that let ranking be composed declaratively. SolrCloud adds sharding, replication and leader election coordinated through ZooKeeper.

Reach for it in a JVM shop that wants deep faceted search over structured content, or where it already runs well, since feature-wise it remains a capable engine and its schema discipline suits catalogues. Weigh the ecosystem honestly, though, because mindshare, managed offerings, log-analytics tooling and vector-search investment have all moved to Elasticsearch and OpenSearch, so a new deployment usually starts there instead.

### SPLADE
**Short:** Learned sparse retrieval model that expands a query/document into weighted vocabulary terms usable by an inverted index.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, search-retrieval/reranking @3, search-retrieval/ann-index-library @3

It runs a masked-language-model head over the input and takes, for every vocabulary term, the maximum activation across positions, producing weights for terms that never appeared in the text, so a document about a laptop also carries weight on notebook and computer. A regularisation term during training pushes most of those weights to zero, keeping the representation sparse enough to store in an ordinary inverted index, which means the expansion is learned while retrieval remains postings-list arithmetic that scales the way lexical search does.

Reach for it when you want the vocabulary-mismatch robustness of embeddings while keeping an inverted index, exact-term matching and interpretable per-term scores, which suits hybrid pipelines well. The costs are a neural pass to encode every document at index time and the query at search time, longer postings lists than plain BM25, and an engine that supports impact or term-weight indexing. Without a GPU, BM25 plus a dense retriever is the practical substitute.

### Thomson Reuters AI
**Short:** Legal research assistant built into Westlaw and Practical Law, answering over licensed case law with citations.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/rag-and-document-processing @1

The retrieval side is what makes it interesting: rather than answering from model memory, the question is run against the publisher's own licensed collections of case law, statutes and secondary sources, and the answer is composed so that every proposition links back to a specific authority the reader can open. Editorially maintained metadata that long predates any language model, such as headnotes and citation-treatment signals showing whether a case has been overruled, is what the retrieval ranks and filters on, and it is a substantial moat over general web search.

As an architecture it is the strongest argument for grounding over fine-tuning in a professional domain, where content changes constantly, provenance is mandatory, and a fabricated citation is a sanctionable error rather than a poor user experience. The general lesson is that the corpus, its metadata and expert evaluation dominate the choice of model. The counterweight is licensing cost and lock-in around content nobody else is permitted to index.

### Typesense
**Short:** Open-source typo-tolerant search engine with an inverted index plus vector search, tuned for instant-search UIs.
**Kind:** tech
**Lang:** *
**Roles:** search-retrieval/lexical-and-hybrid-search @1, data-stores/vector-store @2

It is a single Go binary keeping its indexes in memory, which sets both its speed and its sizing rule, since RAM must hold the dataset and capacity planning becomes arithmetic rather than guesswork. Typo tolerance is part of matching rather than bolted on, ranking combines a text-match score with fields you nominate such as popularity or rating through an explicit `sort_by`, and a Raft-based cluster of three nodes provides high availability. Vector fields and a hybrid mode let semantic and keyword matches be fused in one query.

Reach for it for instant search over a catalogue or documentation site when you want predictable behaviour, a simple JSON API and self-hosting without a JVM. It is not a log store and not an analytics engine, so there are no deep aggregations, no time-series tiering and no billion-document ambitions on one node. Meilisearch is its closest analogue, and Elasticsearch or OpenSearch is where you go when the requirement grows into analytics.

### Unstructured.io
**Short:** Document ingestion library that parses PDFs, Office files and HTML into structure-aware elements for chunking.
**Kind:** tech
**Lang:** python
**Roles:** search-retrieval/rag-and-document-processing @1

It routes a file by type, using layout models and OCR for a scanned PDF and native parsers for `.docx`, HTML, email and slides, and returns a list of typed elements such as `Title`, `NarrativeText`, `Table` and `ListItem`, each carrying metadata like page number and source, instead of one undifferentiated blob of text. Those types are what make structure-aware chunking possible, so a section stays with its heading and a table survives as a unit rather than being sliced through the middle, which is one of the most common causes of nonsense context in RAG. Reach for it when the corpus is real-world documents in mixed formats. The high-resolution strategies are slow and model-backed, so partition once into a store rather than on every query.

### Vector index
**Short:** Generic term for the structure that makes embedding similarity search fast, from brute-force flat to HNSW or IVF.
**Kind:** concept
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, data-stores/vector-store @3

The structures divide into a few families. A flat index compares the query against every vector, exact and perfectly adequate up to perhaps a hundred thousand entries. IVF clusters the vectors and searches only the `nprobe` nearest cells. HNSW builds a layered proximity graph and walks it greedily, with `M` fixing graph degree and memory and `efSearch` the query-time breadth. Quantization is orthogonal to all of them, compressing each vector so that more of the index fits in RAM at some loss of precision.

The choice is usually made for you by whichever store you adopted, so the real work is tuning the one knob that trades recall for latency and verifying it against exact search on real queries, because an approximate index degrades silently rather than erroring. Two things teams learn late: a highly selective metadata filter interacts badly with graph indexes, and updates and deletions are cheap in some structures and effectively require a rebuild in others.

### Voyage AI
**Short:** Managed embedding provider whose voyage-4 family includes domain models for code, finance and legal text.
**Kind:** model
**Lang:** *
**Roles:** search-retrieval/ann-index-library @1, search-retrieval/rag-and-document-processing @2, search-retrieval/reranking @3

The pitch is specialisation. Alongside general models there are checkpoints trained for code, finance, law and multilingual text, and on a matching corpus a domain model beats a larger general one, because retrieval quality depends far more on whether the training distribution resembles your documents than on parameter count. The models support quantised output types and truncatable dimensions, so an index can be shrunk substantially and the top candidates rescored at full precision, and companion rerankers cover the second stage.

Reach for it when your corpus sits squarely in one of those domains and retrieval accuracy is what the product is judged on. It is a hosted, metered dependency, so cost grows with corpus size and text leaves your network, which decides the question outright in some organisations. Open-weight families such as BGE or Qwen embeddings remain the answer where self-hosting is required, and the honest test in either case is recall on your own queries.
