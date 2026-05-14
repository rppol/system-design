# LlamaIndex — Deep Dive

---

## 1. Concept Overview

LlamaIndex (formerly GPT Index) is a data framework for building LLM applications over custom data. Its primary specialization is the RAG pipeline: loading documents from 150+ sources, chunking and indexing them, and enabling multiple retrieval strategies (vector search, keyword search, graph traversal, tree-based summarization). Beyond RAG, LlamaIndex provides data agents that can query structured databases, APIs, and document collections.

Where LangChain is a general-purpose orchestration framework, LlamaIndex is depth-first on data access patterns. It has the most sophisticated retrieval abstractions in the ecosystem: sentence-window retrieval, recursive retrieval, auto-merging, sub-question decomposition, and query routing.

**Current version**: llama-index-core 0.10.x (2024)
**Production adoption signal**: Used by Replit (codebase indexing), Jerry (insurance Q&A), Vanna.AI (SQL generation), and thousands of enterprise RAG applications.

---

## 2. Intuition

**One-line analogy**: LlamaIndex is the database layer for LLMs — it ingests your data, builds the right index for each access pattern, and serves it efficiently through query engines.

**Mental model**: A traditional database has tables, indexes (B-tree, hash), and query engines (SQL, full-text). LlamaIndex does the same for unstructured data: data connectors load documents, node parsers chunk them, various index types (VectorStore, Summary, Knowledge Graph) structure them differently, and query engines implement retrieval strategies. The "SQL" equivalent is a natural language query.

**Why it matters**: Most RAG failures come from retrieval problems, not generation problems. LlamaIndex provides more retrieval strategies than any other framework. If your RAG quality is poor, LlamaIndex's advanced retrieval techniques often fix it without changing the LLM.

**Key insight**: LlamaIndex separates the indexing step (offline, expensive) from the retrieval step (online, fast). This separation is critical for production — you can re-index nightly without touching the query path, and optimize the query path without re-indexing.

---

## 3. Core Principles

**Nodes as the unit of information**: Documents are chunked into `TextNode` objects. Each node has text, metadata (source, page number, creation date), and relationships to adjacent nodes (previous/next, parent/child). These relationships enable context-window retrieval patterns.

**Index as a data structure**: An index is not just "embeddings in a vector database." Different index types structure nodes differently: `VectorStoreIndex` for semantic search, `SummaryIndex` for iterating all documents, `KnowledgeGraphIndex` for entity relationships, `TreeIndex` for hierarchical summarization.

**Query engines and retrievers are separate**: A `Retriever` fetches relevant nodes from an index. A `QueryEngine` wraps a retriever with an LLM to synthesize an answer. This separation allows mixing retrievers and synthesizers — e.g., use LlamaIndex's retriever with a LangChain chain for synthesis.

**Pipelines are composable**: `IngestionPipeline` for offline indexing, `QueryPipeline` (or `Router`, `RetrieverQueryEngine`) for online querying. Each step is a modular component that can be swapped.

**ServiceContext / Settings**: Global configuration (LLM, embed model, chunk size, chunk overlap) is set via `Settings` object (llama-index-core 0.10+) rather than passed to every component. This prevents configuration drift.

---

## 4. Types / Architectures / Strategies

### Index Types

| Index | Storage | Best For | Tradeoff |
|-------|---------|----------|---------|
| `VectorStoreIndex` | Vector DB (Pinecone, Chroma, Weaviate) | Semantic similarity search | Best general purpose |
| `SummaryIndex` | In-memory list | Iterating all docs, summaries | Slow for large corpuses |
| `KnowledgeGraphIndex` | Graph DB (Neo4j, Nebula) | Entity relationships, multi-hop | Complex setup |
| `TreeIndex` | In-memory tree | Hierarchical Q&A, summarization | High build cost |
| `KeywordTableIndex` | Keyword map | Exact keyword matching | No semantic understanding |

### Retrieval Strategies

| Strategy | How It Works | When to Use |
|----------|-------------|-------------|
| Top-K vector | Embed query, cosine similarity | Default, most cases |
| BM25 | Keyword matching | When exact terms matter |
| Hybrid | BM25 + vector, reranked | Best precision for most corpuses |
| Sentence window | Index sentences, retrieve ±2 sentences for context | Short sentences, need surrounding context |
| Auto-merging | Index small chunks, fetch parent if children retrieved | Nested structure (chapters → paragraphs → sentences) |
| Recursive retrieval | Index contains summaries pointing to detailed indices | Multi-document, diverse topics |
| Sub-question | Decompose into sub-questions, answer each, synthesize | Complex multi-part questions |
| Router | Classify question, route to specialized index | Multiple data sources |

### Agent Types

| Agent | Description |
|-------|-------------|
| `ReActAgent` | ReAct pattern; works with any model |
| `FunctionCallingAgent` | OpenAI/Anthropic function calling; more reliable |
| `OpenAIAgent` | Legacy OpenAI-specific agent; deprecated |

---

## 5. Architecture Diagrams

### LlamaIndex RAG Pipeline

```
INGESTION (offline)
  Raw Files (PDF, DOCX, HTML, Notion, Confluence, GitHub, ...)
       |
  [Data Connectors / SimpleDirectoryReader]
       |
  List[Document]  (metadata-rich document objects)
       |
  [Node Parsers / Text Splitters]
  (chunk_size=512, chunk_overlap=64 by default)
       |
  List[TextNode]  (with parent/prev/next relationships)
       |
  [Transformations: metadata extractors, embeddings]
       |
  [VectorStoreIndex.from_documents()]
       |
  Vector DB (Chroma / Pinecone / PGVector / ...)
  + DocStore (optional, for parent doc retrieval)

QUERY (online)
  User Question: "What is the refund policy?"
       |
  [QueryEngine.query(question)]
       |
  [Retriever]
  embed(question) → cosine search → top_k=4 TextNodes
       |
  [NodePostprocessors]
  (reranker, similarity threshold filter, metadata filter)
       |
  Filtered TextNodes (top 3 after rerank)
       |
  [Response Synthesizer]
  prompt = "Answer based on context:\n{context}\nQuestion: {question}"
  LLM.generate(prompt)
       |
  Response + Source Nodes (citations)
```

### Sentence Window Retrieval

```
Index build:
  "The cat sat on the mat." → Node(id=1, text="The cat sat on the mat.")
  "The mat was red."        → Node(id=2, text="The mat was red.")
  "The cat was hungry."     → Node(id=3, text="The cat was hungry.")
  (each node stores window: prev_id, next_id)

Query: "What color was the mat?"
  → embed query → most similar: Node(id=2) [score=0.91]
  → sentence window retrieval expands to window of 3:
      [Node(id=1), Node(id=2), Node(id=3)]
  → LLM sees full context, not just the matching sentence
  → Answer: "The mat was red."
```

### Sub-Question Decomposition

```
Question: "Compare the revenue growth and employee count of Apple and Microsoft in 2023"

[SubQuestionQueryEngine]
       |
  [Question Generator LLM]
       |
  Sub-questions:
    Q1: "What was Apple's revenue growth in 2023?" → routes to Apple_index
    Q2: "What was Microsoft's revenue growth in 2023?" → routes to Microsoft_index
    Q3: "What was Apple's employee count in 2023?" → routes to Apple_index
    Q4: "What was Microsoft's employee count in 2023?" → routes to Microsoft_index
       |
  [all 4 queries run, answers collected]
       |
  [Synthesis LLM]
  "Based on the answers to the sub-questions, here is the comparison..."
       |
  Final answer with full comparison
```

---

## 6. How It Works — Detailed Mechanics

### Basic RAG Setup

```python
from llama_index.core import VectorStoreIndex, SimpleDirectoryReader, Settings
from llama_index.llms.openai import OpenAI
from llama_index.embeddings.openai import OpenAIEmbedding

# Global settings (avoids passing to every component)
Settings.llm = OpenAI(model="gpt-4o", temperature=0)
Settings.embed_model = OpenAIEmbedding(model="text-embedding-3-small")
Settings.chunk_size = 512
Settings.chunk_overlap = 64

# Load documents from directory (PDF, TXT, DOCX, HTML, etc.)
documents = SimpleDirectoryReader("data/").load_data()

# Build vector index (embeds all nodes, stores in memory)
index = VectorStoreIndex.from_documents(documents)

# Query engine wraps retriever + synthesizer
query_engine = index.as_query_engine(similarity_top_k=4)

response = query_engine.query("What is the return policy?")
print(response.response)
# Access source nodes for citations
for node in response.source_nodes:
    print(f"Source: {node.metadata['file_name']}, Score: {node.score:.3f}")
```

### Persistent Vector Store (Pinecone)

```python
from llama_index.vector_stores.pinecone import PineconeVectorStore
from llama_index.core import StorageContext
import pinecone

pinecone_client = pinecone.Pinecone(api_key=os.environ["PINECONE_API_KEY"])
pinecone_index = pinecone_client.Index("my-index")

vector_store = PineconeVectorStore(pinecone_index=pinecone_index)
storage_context = StorageContext.from_defaults(vector_store=vector_store)

# Builds index and stores in Pinecone
index = VectorStoreIndex.from_documents(
    documents,
    storage_context=storage_context,
    show_progress=True
)

# Later: load existing index (no re-embedding)
index = VectorStoreIndex.from_vector_store(
    vector_store=vector_store,
    storage_context=storage_context
)
```

### Sentence Window Retrieval

```python
from llama_index.core.node_parser import SentenceWindowNodeParser
from llama_index.core.postprocessor import MetadataReplacementPostProcessor

# Parser creates nodes at sentence level, stores window in metadata
parser = SentenceWindowNodeParser.from_defaults(
    window_size=3,  # ±3 sentences around retrieved sentence
    window_metadata_key="window",
    original_text_metadata_key="original_text",
)

# Build index with sentence-level nodes
nodes = parser.get_nodes_from_documents(documents)
index = VectorStoreIndex(nodes)

# Postprocessor replaces the single sentence with its full window
postprocessor = MetadataReplacementPostProcessor(
    target_metadata_key="window"
)

query_engine = index.as_query_engine(
    similarity_top_k=6,  # retrieve more sentences (small chunks)
    node_postprocessors=[postprocessor]
)

response = query_engine.query("Explain the concept.")
# LLM sees expanded context window, not just the matching sentence
```

### Auto-Merging Retrieval

```python
from llama_index.core.node_parser import HierarchicalNodeParser, get_leaf_nodes
from llama_index.core.retrievers import AutoMergingRetriever
from llama_index.core.storage.docstore import SimpleDocumentStore

# Creates multi-level chunks: 512 (leaf) → 1024 (parent) → 2048 (root)
parser = HierarchicalNodeParser.from_defaults(
    chunk_sizes=[2048, 1024, 512]
)
nodes = parser.get_nodes_from_documents(documents)
leaf_nodes = get_leaf_nodes(nodes)

# Store all nodes (needed for parent retrieval)
docstore = SimpleDocumentStore()
docstore.add_documents(nodes)

storage_context = StorageContext.from_defaults(docstore=docstore)
index = VectorStoreIndex(leaf_nodes, storage_context=storage_context)

# Auto-merging: if many leaf nodes from same parent are retrieved, use parent instead
base_retriever = index.as_retriever(similarity_top_k=12)
retriever = AutoMergingRetriever(
    base_retriever,
    storage_context,
    verbose=True,
    simple_ratio_thresh=0.4  # merge if 40% of parent's children are retrieved
)
```

### Sub-Question Query Engine

```python
from llama_index.core.query_engine import SubQuestionQueryEngine
from llama_index.core.tools import QueryEngineTool

# Build indices for different data sources
apple_index = VectorStoreIndex.from_documents(apple_docs)
microsoft_index = VectorStoreIndex.from_documents(microsoft_docs)

# Wrap as tools with descriptions for the query engine to route to
tools = [
    QueryEngineTool.from_defaults(
        query_engine=apple_index.as_query_engine(),
        name="apple_docs",
        description="Annual reports and earnings for Apple Inc."
    ),
    QueryEngineTool.from_defaults(
        query_engine=microsoft_index.as_query_engine(),
        name="microsoft_docs",
        description="Annual reports and earnings for Microsoft Corporation."
    )
]

sub_question_engine = SubQuestionQueryEngine.from_defaults(
    query_engine_tools=tools,
    use_async=True  # runs sub-questions in parallel
)

response = sub_question_engine.query(
    "Compare Apple and Microsoft's revenue growth in 2023"
)
```

### LlamaIndex Data Agent

```python
from llama_index.core.agent import FunctionCallingAgent
from llama_index.core.tools import QueryEngineTool, FunctionTool
import json

# Tool 1: Query knowledge base
kb_tool = QueryEngineTool.from_defaults(
    query_engine=kb_index.as_query_engine(similarity_top_k=5),
    name="knowledge_base",
    description="Internal company knowledge base with policies, procedures, and documentation."
)

# Tool 2: Custom function tool
def lookup_customer(customer_id: str) -> str:
    """Look up customer account information by ID."""
    customer = db.get_customer(customer_id)
    return json.dumps(customer.to_dict())

customer_tool = FunctionTool.from_defaults(fn=lookup_customer)

# Build agent
agent = FunctionCallingAgent.from_tools(
    tools=[kb_tool, customer_tool],
    llm=OpenAI(model="gpt-4o"),
    verbose=True,
    max_function_calls=10  # prevents infinite tool loops
)

response = agent.chat("Look up customer C-1234 and find their applicable return policy.")
```

### Ingestion Pipeline with Transformations

```python
from llama_index.core.ingestion import IngestionPipeline
from llama_index.core.node_parser import TokenTextSplitter
from llama_index.core.extractors import TitleExtractor, QuestionsAnsweredExtractor
from llama_index.vector_stores.chroma import ChromaVectorStore

pipeline = IngestionPipeline(
    transformations=[
        TokenTextSplitter(chunk_size=512, chunk_overlap=64),
        TitleExtractor(nodes=5),  # extract document title, add to metadata
        QuestionsAnsweredExtractor(questions=3),  # generate Q&A pairs for better retrieval
        OpenAIEmbedding(),  # generate embeddings
    ],
    vector_store=ChromaVectorStore(chroma_collection=collection),
    cache=IngestionCache(),  # skip re-processing unchanged documents
)

nodes = pipeline.run(documents=documents, show_progress=True)
```

---

## 7. Real-World Examples

**Replit**: Uses LlamaIndex for codebase indexing in their AI coding features. Custom node parsers split code by function/class boundaries instead of token count. Metadata includes file path, language, and AST relationships for more accurate code retrieval.

**Jerry (insurance tech)**: Parses insurance policy documents (PDFs with complex layouts) using LlamaIndex's document loaders, builds per-customer policy indices, and answers coverage questions. Auto-merging retrieval handles policy documents with nested section structure.

**Vanna.AI**: SQL generation product uses LlamaIndex to index SQL documentation, schema definitions, and query examples. VectorStoreIndex + KnowledgeGraphIndex combined for schema relationship traversal.

**Notion AI competitors**: Multiple startups use LlamaIndex with Notion connectors (NotionPageReader) to build Q&A over team wikis. SimpleDirectoryReader handles attachments; parent-child hierarchy preserved.

**Multi-document research tools**: Law firm tools use SubQuestionQueryEngine to research across multiple case documents simultaneously — each case indexed separately, sub-questions route to relevant cases.

---

## 8. Tradeoffs

| Dimension | LlamaIndex | LangChain | Custom RAG |
|-----------|-----------|-----------|-----------|
| RAG sophistication | Highest | Medium | Variable |
| General agent patterns | Medium | Highest | Variable |
| Integration breadth | High (150+ loaders) | Highest (300+ tools) | None |
| Learning curve | Medium | Medium | Low (just Python) |
| Debugging transparency | Medium | Medium (with LangSmith) | High |
| Retrieval quality defaults | Better (sentence window, auto-merge) | Worse (basic top-k) | Your choice |
| Structured data (SQL, APIs) | Good (SQL index, Pandas index) | Good | Your choice |
| Version stability | Medium | Low (frequent breaks) | N/A |

**Retrieval Strategy Comparison:**

| Strategy | Latency | Precision | Recall | Setup |
|----------|---------|-----------|--------|-------|
| Top-K vector | Fast | Medium | Good | Easy |
| BM25 only | Fast | Good (for keywords) | Medium | Easy |
| Hybrid | Medium | Good | Good | Medium |
| Sentence window | Medium | High | Good | Medium |
| Auto-merging | Medium | High | High | Complex |
| Sub-question | Slow (N queries) | Highest | Highest | Complex |

---

## 9. When to Use / When NOT to Use

**Use LlamaIndex when:**
- RAG quality is the primary concern — LlamaIndex's retrieval strategies outperform LangChain defaults
- Many diverse data sources (PDF, Notion, Confluence, GitHub, databases) — 150+ data connectors
- Complex documents (nested hierarchies, tables, multi-section PDFs) — specialized parsers
- Multi-document synthesis required — SubQuestionQueryEngine, RouterQueryEngine
- Structured + unstructured data combined — SQL index, Pandas query engine alongside vector index
- Need to understand retrieval metrics and iterate on them

**Do NOT use LlamaIndex when:**
- Application is primarily about agent orchestration with many custom tools — LangGraph + LangChain is better
- Team needs maximum control over retrieval — LlamaIndex abstractions can be hard to override
- Very simple RAG (single document type, basic search) — direct vector DB + LangChain is simpler
- Streaming responses are critical — LlamaIndex streaming is less polished than LangChain LCEL

---

## 10. Common Pitfalls

**Pitfall 1: Default chunk size is wrong for your domain**
Default `chunk_size=1024` tokens works for general prose. For code: split by function/class, not token count. For PDFs with tables: use `PDFMinerLoader` not `SimpleDirectoryReader` — otherwise tables are lost. For HTML: strip tags before chunking. Team built RAG over API documentation with default chunker; retrieved chunks contained half-functions, leading to syntax errors in generated code. Fix: `CodeSplitter` from `llama_index.core.node_parser`.

**Pitfall 2: Not persisting the index**
```python
# WRONG: re-embeds all documents every startup (expensive + slow)
index = VectorStoreIndex.from_documents(documents)  # runs every time

# CORRECT: build once, persist, load from disk
# Build:
index = VectorStoreIndex.from_documents(documents)
index.storage_context.persist("./storage")
# Load:
from llama_index.core import StorageContext, load_index_from_storage
storage_context = StorageContext.from_defaults(persist_dir="./storage")
index = load_index_from_storage(storage_context)
```

**Pitfall 3: Global Settings mutation in tests**
`Settings.llm = OpenAI(...)` mutates global state. If tests run in parallel, one test's settings affect another. Fix: use `Settings` as a context manager or mock it with `unittest.mock.patch`.

**Pitfall 4: similarity_top_k too small for reranking**
Common pattern: retrieve top-K, then rerank to top-3. If `similarity_top_k=3` and you then rerank, you've only reranked 3 candidates. The correct pattern: `similarity_top_k=15` (retrieve many), then rerank to `top_n=3`. Teams set `similarity_top_k=3` and wondered why reranking didn't improve quality — it had no candidates to choose from.

**Pitfall 5: Ignoring metadata for filtering**
All documents in the same index means a query about "2023 policy" retrieves chunks from "2019 policy" too. Solution: add `doc_date`, `doc_type`, `department` to node metadata during ingestion, then use `MetadataFilters` at query time:
```python
from llama_index.core.vector_stores import MetadataFilters, ExactMatchFilter
query_engine = index.as_query_engine(
    filters=MetadataFilters(filters=[
        ExactMatchFilter(key="year", value="2023")
    ])
)
```

**Pitfall 6: SubQuestion engine cost explosion**
SubQuestionQueryEngine generates N sub-questions and executes N LLM queries + N retrieval calls. For complex questions: 8 sub-questions × (1 retrieval + 1 synthesis) × GPT-4o = $0.80/query. In production, a customer asked a complex question that generated 12 sub-questions, costing $1.20 for a single query. Fix: `SubQuestionQueryEngine.from_defaults(num_questions=3)` to limit sub-questions, or use it only for confirmed complex queries.

**Pitfall 7: KnowledgeGraphIndex quality depends on LLM extraction**
Building a knowledge graph uses an LLM to extract entity-relationship triples from text. With GPT-3.5-turbo, extraction quality is poor; with GPT-4o it's acceptable but expensive ($0.05-0.20 per document at 1K tokens). Teams built KG indices with a cheap model, got low-quality graphs, and saw worse Q&A results than basic vector search. Recommendation: use KnowledgeGraphIndex only when your data has strong entity relationships; test extraction quality before committing to it.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| `llama-index-core` | Core framework | 0.10.x — breaking change from 0.9 |
| `llama-index-llms-openai` | LLM integration | Separate package since 0.10 |
| `llama-index-embeddings-openai` | Embedding model | Separate package |
| `llama-index-vector-stores-pinecone` | Vector store | One package per integration |
| `llama-index-retrievers-bm25` | BM25 retriever | Requires `rank_bm25` |
| `llama-index-postprocessor-cohere-rerank` | Reranking | Cohere Rerank API |
| `LlamaHub` | Community integrations | 150+ data connectors, vector stores, LLMs |
| `LlamaCloud` | Managed ingestion + parsing | Enterprise parsing service (handles tables, images) |

**Version notes:**
- llama-index 0.9.x: monolithic package, `ServiceContext` for config
- llama-index-core 0.10.x (Jan 2024): split into packages, `Settings` replaces `ServiceContext`, namespace changed from `llama_index` to `llama_index.core`
- Migration required from 0.9 to 0.10: `from llama_index import ...` → `from llama_index.core import ...`

---

## 12. Interview Questions with Answers

**Q: What is LlamaIndex and what problem does it solve compared to LangChain?**
LlamaIndex is a data framework specialized for connecting LLMs to custom data sources. It solves the retrieval quality problem: LangChain provides basic top-K vector search, while LlamaIndex offers 10+ retrieval strategies (sentence window, auto-merging, sub-question decomposition, recursive retrieval) tuned for production quality. LangChain is better for general agent orchestration and tool use; LlamaIndex is better when retrieval quality is the primary concern or when ingesting diverse, complex document types.

**Q: Explain the difference between an Index, a Retriever, and a QueryEngine in LlamaIndex.**
An `Index` is the data structure built from documents (e.g., `VectorStoreIndex` stores embeddings in a vector database). A `Retriever` queries the index to return relevant nodes given a question (e.g., `VectorIndexRetriever` does cosine similarity search). A `QueryEngine` wraps a retriever with an LLM synthesizer — it retrieves nodes and generates an answer. The separation is key: you can swap retrievers (switch from top-K to BM25) without changing the synthesizer, or swap synthesizers without changing retrieval.

**Q: What is sentence window retrieval and why does it outperform basic top-K?**
Sentence window retrieval indexes documents at the sentence level (small chunks) for precise matching, but when a sentence is retrieved, it expands the context window to include surrounding sentences (±2-3 sentences). Basic top-K retrieves at the chunk level (512+ tokens) — the chunk may contain the answer buried in irrelevant text. Sentence window finds the exact relevant sentence, then provides enough surrounding context for the LLM to understand it. Typical improvement: 15-25% better faithfulness scores on RAG evaluation benchmarks compared to basic 512-token chunking.

**Q: What is auto-merging retrieval?**
Auto-merging builds a hierarchical index: small leaf chunks (128-256 tokens), medium parent chunks (512 tokens), large root chunks (1024+ tokens). During retrieval, if multiple leaf chunks from the same parent are retrieved, they are automatically merged and the parent chunk is returned instead. This prevents the LLM from receiving fragmented information — if a section of a document answers a question, the whole section is returned rather than disconnected sentences. Best for: technical documentation, policy documents, and any content with natural section hierarchy.

**Q: How does the SubQuestionQueryEngine work?**
Given a complex query, a question-generator LLM decomposes it into sub-questions. Each sub-question is routed to the most relevant `QueryEngineTool` (different indices for different data sources). Sub-questions run in parallel (with `use_async=True`). Answers are collected and a synthesis LLM generates the final answer by combining them. Limitation: expensive (N LLM calls), can generate redundant or irrelevant sub-questions. Best for: well-defined multi-source research questions. Not for: simple factual queries or when cost is a concern.

**Q: What is the difference between `from_documents` and loading from a persisted index?**
`VectorStoreIndex.from_documents(documents)` processes all documents: runs node parsers, generates embeddings for all chunks, and loads them into the vector store. This is expensive (minutes to hours for large corpora, significant API costs). Loading from a persisted index (`load_index_from_storage`) reads the stored metadata and reconnects to the vector store without re-processing. In production: build the index once during deployment, persist to disk or vector DB, load on startup. Rebuild only when documents change (incremental update with `index.insert(new_document)`).

**Q: How do you handle metadata filtering in LlamaIndex?**
Add metadata to documents during loading (or set it on `TextNode` objects). At query time, pass `MetadataFilters` to the query engine or retriever. Example: `filters=MetadataFilters(filters=[ExactMatchFilter(key="department", value="legal")])`. For range filters: `FilterCondition(key="year", value=2023, operator=FilterOperator.GTE)`. Important: the metadata must exist in the vector store (most vector DBs support metadata filtering: Pinecone, Weaviate, Qdrant). Chroma supports basic filtering; Pinecone has the most flexible filter expressions.

**Q: What is the IngestionPipeline and what transformations does it support?**
`IngestionPipeline` is a reusable, cacheable pipeline for document processing. Transformations include: node parsers (TokenTextSplitter, SentenceWindowNodeParser), metadata extractors (TitleExtractor, QuestionsAnsweredExtractor, KeywordExtractor, SummaryExtractor), and embedding models. The pipeline caches processed nodes by document hash — if a document is unchanged, re-running the pipeline skips it. This enables incremental indexing. Transformations run sequentially; each receives a list of nodes and returns a transformed list. Custom transformations are possible by subclassing `BaseTransformation`.

**Q: How do you combine LlamaIndex retrieval with LangChain for generation?**
LlamaIndex retrievers can be wrapped as LangChain Retrievers:
```python
from llama_index.core.langchain_helpers.text_splitter import LangchainNodeParser
# Or use LlamaIndex retriever output directly
nodes = retriever.retrieve(query)
docs = [n.node.get_content() for n in nodes]
# Pass docs to LangChain chain
```
Alternatively, use LlamaIndex's `QueryEngineTool` in a LangChain agent. The hybrid pattern is common: LlamaIndex for retrieval quality, LangChain for orchestration and tool use.

**Q: What changed in LlamaIndex 0.10 vs 0.9?**
Breaking changes: (1) Package split — monolithic `llama_index` package split into `llama-index-core` + provider packages (`llama-index-llms-openai`, etc.); (2) Namespace change — `from llama_index import ...` → `from llama_index.core import ...`; (3) `ServiceContext` deprecated in favor of `Settings` singleton; (4) `LLMPredictor` and `PromptHelper` removed, replaced by direct `Settings.llm` configuration. Migration from 0.9 to 0.10 requires updating all imports and removing `ServiceContext` construction. Reason for change: modular architecture reduces package size and allows independent versioning of integrations.

**Q: How does the Router Query Engine work?**
`RouterQueryEngine` uses an LLM to select which `QueryEngineTool` to route a query to. Given multiple tools (product docs, pricing index, support FAQ), the router generates a natural language description for each tool and asks the LLM which is most appropriate for the current query. Supports single routing (pick one) and multi-routing (pick all relevant). Multi-routing is similar to SubQuestion but without decomposition — useful when the same question can be answered from multiple sources. Limitation: LLM routing adds 1-2 seconds of latency; for high-traffic systems, use a classifier-based router instead.

**Q: How do you evaluate RAG quality with LlamaIndex?**
LlamaIndex provides `RAGEvaluator` integrating with RAGAs metrics: (1) Faithfulness — does the answer stay within the retrieved context? LLM-as-judge compares answer to source nodes; (2) Answer Relevance — does the answer address the question? LLM generates hypothetical questions from the answer and measures overlap; (3) Context Precision/Recall — are the retrieved nodes relevant? Uses LLM to judge relevance of each retrieved node. Concrete workflow: collect 100 production queries, create ground truth answers, run evaluators on production data, set threshold (faithfulness > 0.85), alert if metrics drop below threshold after updates.

**Q: How do LlamaIndex data agents differ from LangChain agents?**
LlamaIndex data agents specialize in data-access tools: `QueryEngineTool` (query an index), `FunctionTool` (call a Python function). They use the same underlying patterns (ReAct or function calling) but are optimized for the case where the primary actions are querying data sources. LangChain agents have a broader ecosystem of pre-built tools (web search, calculator, shell, email, calendar). In practice: use LlamaIndex agents when the primary tools are indices/databases; use LangChain/LangGraph agents when you need diverse tool types. The frameworks are complementary and can be mixed.

**Q: What is LlamaCloud and when should you use it?**
LlamaCloud is a managed service providing: (1) LlamaParse — cloud-based document parsing that handles tables, images, and complex PDF layouts better than local parsers; (2) Managed ingestion pipelines — scheduled re-indexing without managing infrastructure; (3) Managed indices — vector storage and retrieval without running a vector database. Use LlamaCloud when: complex document parsing is needed (LlamaParse outperforms local parsers for tables and multi-column PDFs), or when you want to skip vector database management. Self-host when: data cannot leave your infrastructure (LlamaCloud requires sending documents to their API).

**Q: How do you handle document updates in a production LlamaIndex setup?**
Three patterns: (1) Full rebuild — delete and rebuild the entire index on a schedule (nightly). Simple but slow and costly for large corpora; (2) Incremental update — use `IngestionPipeline` with caching; only processes changed documents. Requires comparing document hashes to detect changes; (3) Document-level refresh — `index.delete_ref_doc(doc_id)` removes all nodes for a document, then `index.insert(updated_doc)` adds the new version. Pattern 2 is recommended for most production setups: `pipeline.run(documents=all_docs)` with caching skips unchanged docs automatically.

---

## 13. Best Practices

1. **Use `Settings` (0.10+) not `ServiceContext`** — global configuration; configure once at startup.
2. **Always persist the index** — never rebuild on startup; add `index.storage_context.persist()` after building.
3. **Start with basic top-K, then add complexity** — measure retrieval quality first; add sentence window or auto-merging only if basic retrieval is insufficient.
4. **Set `similarity_top_k` higher than you think** — retrieve 12-20 candidates, then rerank to top 3; better coverage than directly fetching top 3.
5. **Add metadata during ingestion** — document date, type, department; enable filtering at query time.
6. **Use `use_async=True` for SubQuestionQueryEngine** — sub-questions run in parallel, cutting latency by 60-80%.
7. **Cache ingestion pipelines** — `IngestionPipeline(cache=IngestionCache())` skips re-processing unchanged docs.
8. **Use LlamaParse for complex PDFs** — superior table handling vs PyPDF2/pdfminer for business documents.
9. **Evaluate with RAGAs metrics** — faithfulness and answer relevance; set thresholds and alert on regression.
10. **Limit SubQuestionQueryEngine calls** — set `num_questions=3-5` to control cost; don't use it for simple queries.

---

## 14. Case Study: Legal Document Q&A System

**Scenario**: A law firm needs Q&A over 50,000 legal documents (contracts, case files, regulatory filings). Requirements: precise retrieval with source citations, multi-document synthesis for comparative analysis, access control by client matter, and sub-second response time for simple queries.

### Architecture

```
INGESTION PIPELINE (runs nightly):
  Legal PDFs (contracts, filings, case docs)
       |
  [LlamaParse]  (handles complex PDF layouts, tables of contents)
       |
  [HierarchicalNodeParser] (chunk_sizes=[2048, 512, 128])
       |
  [TitleExtractor + KeywordExtractor]  (adds document type, parties, date to metadata)
       |
  [OpenAIEmbedding]
       |
  [Pinecone]  (metadata: matter_id, doc_type, date, parties)
  + [SimpleDocumentStore]  (stores parent nodes for auto-merging)

QUERY ENGINE (per user request):
  Query + user.matter_ids
       |
  MetadataFilters(matter_id IN user.matter_ids)  (access control)
       |
  AutoMergingRetriever  (retrieves from Pinecone, merges related nodes)
       |
  CohereRerank(top_n=4)
       |
  GPT-4o Synthesizer (with citation format)
       |
  Answer + Source Citations (file, page, section)
```

### Multi-Document Comparison

```python
# Per-matter query engines for sub-question routing
matter_tools = []
for matter_id in user.matter_ids:
    matter_engine = build_matter_query_engine(matter_id)
    matter_tools.append(QueryEngineTool.from_defaults(
        query_engine=matter_engine,
        name=f"matter_{matter_id}",
        description=f"Legal documents for matter {matter_id}: {matter_descriptions[matter_id]}"
    ))

# SubQuestion for cross-matter analysis
sub_question_engine = SubQuestionQueryEngine.from_defaults(
    query_engine_tools=matter_tools,
    use_async=True,
    num_questions=4  # cost control
)

# Route: simple queries go to single-matter retriever, complex to sub-question
router = RouterQueryEngine(
    selector=LLMSingleSelector.from_defaults(),
    query_engine_tools=[
        QueryEngineTool.from_defaults(simple_engine, "single_matter", "single matter question"),
        QueryEngineTool.from_defaults(sub_question_engine, "multi_matter", "cross-matter analysis"),
    ]
)
```

### Results

- Retrieval precision@3: 0.89 (auto-merging vs 0.71 for basic top-K)
- Response latency P50: 1.2 seconds (cached embeddings, Pinecone ANN search <100ms)
- Cost per query: $0.08 average (GPT-4o synthesis + Cohere rerank)
- Multi-matter queries: 3.1 seconds average (4 sub-questions in parallel)
- Access control violations: 0 (Pinecone metadata filters enforced at retrieval layer)
