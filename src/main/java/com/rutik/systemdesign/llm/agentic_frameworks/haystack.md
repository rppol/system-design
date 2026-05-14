# Haystack — Deep Dive

---

## 1. Concept Overview

Haystack is an open-source LLM framework from deepset (now Haystack.ai) built around the concept of pipelines — directed acyclic graphs of components that transform data. Where LangChain uses a Python pipe operator and LangGraph uses state machines, Haystack uses a declarative pipeline definition with typed inputs and outputs, making it particularly suited for production NLP and RAG deployments.

Haystack 2.0 (released early 2024) was a complete rewrite from Haystack 1.x, introducing: a cleaner component protocol, typed pipeline connections, native async support, and a more Pythonic API. It is the most production-grade pipeline framework for RAG, with strong support for document processing, hybrid retrieval, and custom component development.

**Current version**: haystack-ai 2.x (2024)
**Production adoption signal**: Used by Airbus, Accenture, BMW, Deutsche Telekom for production NLP and RAG systems. deepset's commercial product (deepset Cloud) is built on Haystack.

---

## 2. Intuition

**One-line analogy**: Haystack is like Apache Airflow for LLM pipelines — you define a DAG of typed components, connect them, and execute the pipeline with type safety enforced at runtime.

**Mental model**: A Haystack pipeline is a DAG where each node is a `Component` (data transformer) and edges are typed connections between component outputs and inputs. A RAG pipeline: `[Embedder] → [Retriever] → [PromptBuilder] → [Generator] → output`. Each component has declared input types and output types; Haystack validates that connections are type-compatible at pipeline construction time.

**Why it matters**: Haystack's typed pipeline model prevents a class of bugs common in LangChain — connecting components with incompatible types (retriever output → prompt template that expects a different format). Type validation at construction time catches these errors before runtime.

**Key insight**: Haystack's pipeline model separates concerns cleanly: components are reusable, independently testable units. Swapping a retriever (BM25 → vector) or generator (OpenAI → HuggingFace) is a one-line change. This modularity is the framework's primary advantage for production systems.

---

## 3. Core Principles

**Component protocol**: Every `Component` implements `@component` decorator and declares `@component.output_types(key=type)`. The `run()` method takes typed keyword arguments and returns a dict matching declared output types. This protocol enables the pipeline to validate connections at build time.

**Pipeline as a DAG**: `Pipeline.add_component(name, component)` adds nodes; `Pipeline.connect("source.output_key", "target.input_key")` adds edges. Haystack validates: component exists, output key exists, input key exists, types are compatible.

**Document as the unit of data**: `Document` is the central data class — text content + metadata + embedding + score. Pipelines pass `List[Document]` between retrieval and processing components. This uniformity means all retrieval components produce the same type.

**Declarative + serializable**: Pipelines can be serialized to YAML and loaded from YAML. This enables: version control for pipeline configurations, A/B testing pipeline variants without code changes, and deployment via configuration files.

**Stateless components**: Components are stateless by default. If a component needs initialization (loading a model, connecting to a DB), it does so in `__init__`, not in `run()`. This makes component behavior predictable and enables safe parallelism.

---

## 4. Types / Architectures / Strategies

### Component Categories

| Category | Examples | Purpose |
|----------|---------|---------|
| Embedders | `OpenAITextEmbedder`, `SentenceTransformersTextEmbedder` | Generate embeddings |
| Retrievers | `InMemoryBM25Retriever`, `InMemoryEmbeddingRetriever`, `FilterRetriever` | Retrieve documents |
| Converters | `TextFileToDocument`, `PDFToTextConverter`, `HTMLToDocument` | Load + convert files |
| Splitters | `DocumentSplitter`, `RecursiveCharacterTextSplitter` | Chunk documents |
| Generators | `OpenAIGenerator`, `HuggingFaceLocalGenerator`, `AzureOpenAIGenerator` | LLM text generation |
| Chat generators | `OpenAIChatGenerator`, `AnthropicChatGenerator` | Chat-based LLM calls |
| Prompt builders | `PromptBuilder`, `ChatPromptBuilder` | Format prompts from templates |
| Rankers | `TransformersSimilarityRanker`, `CohereRanker` | Rerank retrieved documents |
| Writers | `DocumentWriter` | Write documents to document store |
| Evaluators | `DeepEvalEvaluator`, `RAGASEvaluator` | Evaluate pipeline quality |

### Pipeline Architectures

1. **Indexing pipeline**: converters → splitter → embedder → document_writer
2. **RAG pipeline**: embedder → retriever → (ranker) → prompt_builder → generator
3. **Hybrid retrieval**: [bm25_retriever, embedding_retriever] → document_joiner → ranker → generator
4. **Extractive QA**: retriever → reader (extractive model) → answer
5. **Conversational RAG**: chat_memory_retriever + embedding_retriever → prompt_builder → chat_generator

---

## 5. Architecture Diagrams

### RAG Pipeline DAG

```
INDEXING PIPELINE (offline):
  [TextFileToDocument]
        |
        v [List[Document]]
  [DocumentSplitter (chunk_size=512)]
        |
        v [List[Document]]
  [OpenAIDocumentEmbedder]
        |
        v [List[Document] with embeddings]
  [DocumentWriter → InMemoryDocumentStore]

QUERY PIPELINE (online):
  query: "What is Haystack?"
        |
        v
  [OpenAITextEmbedder]
        |
        v [embedding vector]
  [InMemoryEmbeddingRetriever (top_k=10)]
        |
        v [List[Document]]
  [TransformersSimilarityRanker (top_k=3)]
        |
        v [List[Document] reranked]
  [PromptBuilder (template with {{documents}} and {{query}})]
        |
        v [str prompt]
  [OpenAIGenerator (model="gpt-4o")]
        |
        v [replies: List[str]]
  Output: "Haystack is an open-source framework..."
```

### Hybrid Retrieval Pipeline

```
Query
  |
  +---> [BM25Retriever] -------> List[Document]
  |                                     |
  +---> [EmbeddingRetriever] -> List[Document]
                                        |
                               [DocumentJoiner (concatenate, dedup)]
                                        |
                               List[Document] (merged + deduped)
                                        |
                               [TransformersSimilarityRanker]
                                        |
                               top-3 List[Document]
                                        |
                               [PromptBuilder]
                                        |
                               [Generator]
                                        |
                               Answer
```

### Component Protocol

```python
@component
class DocumentRanker:
    @component.output_types(documents=List[Document])
    def run(self, documents: List[Document], query: str) -> dict:
        # Type system enforces:
        #   - input 'documents' must be List[Document]
        #   - input 'query' must be str
        #   - return must contain 'documents' key with List[Document]
        scored = [(score(doc, query), doc) for doc in documents]
        return {"documents": [doc for _, doc in sorted(scored)[:3]]}

# Pipeline validates at connection time:
pipeline.add_component("ranker", DocumentRanker())
pipeline.connect("retriever.documents", "ranker.documents")  # List[Document] → List[Document] ✓
pipeline.connect("query_input", "ranker.query")               # str → str ✓
# pipeline.connect("retriever.documents", "ranker.query")    # List[Document] → str ✗ TYPE ERROR at build
```

---

## 6. How It Works — Detailed Mechanics

### Basic RAG Pipeline

```python
from haystack import Pipeline, Document
from haystack.document_stores.in_memory import InMemoryDocumentStore
from haystack.components.retrievers.in_memory import InMemoryBM25Retriever
from haystack.components.generators import OpenAIGenerator
from haystack.components.builders import PromptBuilder
from haystack.components.converters import TextFileToDocument
from haystack.components.preprocessors import DocumentSplitter

# --- Indexing pipeline ---
document_store = InMemoryDocumentStore()

indexing_pipeline = Pipeline()
indexing_pipeline.add_component("converter", TextFileToDocument())
indexing_pipeline.add_component("splitter", DocumentSplitter(split_by="word", split_length=128))
indexing_pipeline.add_component("writer", DocumentWriter(document_store=document_store))
indexing_pipeline.connect("converter.documents", "splitter.documents")
indexing_pipeline.connect("splitter.documents", "writer.documents")

indexing_pipeline.run({"converter": {"sources": ["data/docs/*.txt"]}})

# --- Query pipeline ---
prompt_template = """
Answer based on context:
{% for doc in documents %}
  {{ doc.content }}
{% endfor %}
Question: {{ query }}
Answer:
"""

rag_pipeline = Pipeline()
rag_pipeline.add_component("retriever", InMemoryBM25Retriever(document_store=document_store))
rag_pipeline.add_component("prompt_builder", PromptBuilder(template=prompt_template))
rag_pipeline.add_component("llm", OpenAIGenerator(model="gpt-4o"))
rag_pipeline.connect("retriever.documents", "prompt_builder.documents")
rag_pipeline.connect("prompt_builder.prompt", "llm.prompt")

result = rag_pipeline.run({
    "retriever": {"query": "What is Haystack?", "top_k": 3},
    "prompt_builder": {"query": "What is Haystack?"}
})
print(result["llm"]["replies"][0])
```

### Vector RAG with Persistent Store

```python
from haystack_integrations.document_stores.chroma import ChromaDocumentStore
from haystack_integrations.components.retrievers.chroma import ChromaEmbeddingRetriever
from haystack.components.embedders import OpenAIDocumentEmbedder, OpenAITextEmbedder

document_store = ChromaDocumentStore(persist_path="./chroma_storage")

# Indexing with embeddings
indexing = Pipeline()
indexing.add_component("converter", TextFileToDocument())
indexing.add_component(
    "splitter",
    DocumentSplitter(split_by="sentence", split_length=3, split_overlap=1)
)
indexing.add_component(
    "embedder",
    OpenAIDocumentEmbedder(model="text-embedding-3-small")
)
indexing.add_component("writer", DocumentWriter(document_store=document_store))
indexing.connect("converter.documents", "splitter.documents")
indexing.connect("splitter.documents", "embedder.documents")
indexing.connect("embedder.documents", "writer.documents")

indexing.run({"converter": {"sources": list(Path("data/").glob("*.pdf"))}})

# Query pipeline
rag = Pipeline()
rag.add_component("query_embedder", OpenAITextEmbedder(model="text-embedding-3-small"))
rag.add_component(
    "retriever",
    ChromaEmbeddingRetriever(document_store=document_store, top_k=10)
)
rag.add_component(
    "ranker",
    TransformersSimilarityRanker(model="cross-encoder/ms-marco-MiniLM-L-6-v2", top_k=3)
)
rag.add_component("prompt_builder", PromptBuilder(template=prompt_template))
rag.add_component("llm", OpenAIChatGenerator(model="gpt-4o"))

rag.connect("query_embedder.embedding", "retriever.query_embedding")
rag.connect("retriever.documents", "ranker.documents")
rag.connect("query_embedder.query", "ranker.query")
rag.connect("ranker.documents", "prompt_builder.documents")
rag.connect("prompt_builder.prompt", "llm.messages")

result = rag.run({
    "query_embedder": {"text": "How does Haystack handle document chunking?"},
    "ranker": {"query": "How does Haystack handle document chunking?"},
    "prompt_builder": {"query": "How does Haystack handle document chunking?"}
})
```

### Custom Component

```python
from haystack import component, Document
from typing import List, Optional
from haystack.core.component.types import Variadic

@component
class MetadataFilter:
    """Filter documents by metadata field value."""

    @component.output_types(
        documents=List[Document],
        filtered_count=int
    )
    def run(
        self,
        documents: List[Document],
        filter_key: str,
        filter_value: str
    ) -> dict:
        filtered = [
            doc for doc in documents
            if doc.meta.get(filter_key) == filter_value
        ]
        return {
            "documents": filtered,
            "filtered_count": len(documents) - len(filtered)
        }

# Use in pipeline
pipeline.add_component("filter", MetadataFilter())
pipeline.connect("retriever.documents", "filter.documents")
# Pass filter_key and filter_value at runtime
result = pipeline.run({
    "retriever": {"query": "..."},
    "filter": {"filter_key": "department", "filter_value": "legal", "documents": []}
})
```

### Pipeline Serialization

```python
import yaml

# Serialize pipeline to YAML
pipeline_dict = rag_pipeline.to_dict()
with open("rag_pipeline.yaml", "w") as f:
    yaml.dump(pipeline_dict, f)

# Load pipeline from YAML (no code required)
from haystack import Pipeline
loaded_pipeline = Pipeline.from_dict(yaml.safe_load(open("rag_pipeline.yaml")))
result = loaded_pipeline.run({"retriever": {"query": "..."}})
```

### Async Pipeline

```python
import asyncio

async def answer_questions_concurrently(queries: list[str]) -> list[str]:
    tasks = [
        rag_pipeline.run_async({
            "query_embedder": {"text": q},
            "prompt_builder": {"query": q},
            "ranker": {"query": q}
        })
        for q in queries
    ]
    results = await asyncio.gather(*tasks)
    return [r["llm"]["replies"][0] for r in results]

# Process 20 queries concurrently
answers = asyncio.run(answer_questions_concurrently(queries))
```

### Evaluation Pipeline

```python
from haystack.components.evaluators import DocumentMRREvaluator, FaithfulnessEvaluator

eval_pipeline = Pipeline()
eval_pipeline.add_component(
    "faithfulness",
    FaithfulnessEvaluator(  # LLM-as-judge
        llm=OpenAIChatGenerator(model="gpt-4o")
    )
)
eval_pipeline.add_component(
    "mrr",
    DocumentMRREvaluator()  # Mean Reciprocal Rank for retrieval
)

eval_result = eval_pipeline.run({
    "faithfulness": {
        "questions": test_questions,
        "contexts": retrieved_contexts,
        "predicted_answers": model_answers
    },
    "mrr": {
        "ground_truth_documents": expected_docs,
        "retrieved_documents": actual_retrieved
    }
})

print(f"Faithfulness: {eval_result['faithfulness']['score']:.2%}")
print(f"MRR: {eval_result['mrr']['score']:.2%}")
```

---

## 7. Real-World Examples

**Airbus**: Production RAG over engineering documentation (manuals, specifications, maintenance records). Haystack's typed pipeline model ensures that PDF → chunk → embed → retrieve → generate is validated at deploy time. Custom components handle Airbus-specific document formats.

**BMW Group**: Internal knowledge base over 50,000+ technical documents. Hybrid BM25 + embedding retrieval with custom reranking tuned for automotive terminology. Pipeline serialization enables non-developer deployment of pipeline variants.

**Deutsche Telekom**: Customer support chatbot over product documentation. Haystack's document store abstraction allows switching backends (started with in-memory, moved to Elasticsearch) without changing pipeline code.

**deepset Cloud**: Commercial product built entirely on Haystack. Provides a managed pipeline deployment service. Customers upload YAML pipeline definitions; deepset Cloud executes them without code.

**Healthcare RAG**: Medical literature Q&A over PubMed abstracts. Custom metadata filters for paper date, citation count, and specialty. Haystack's filter component abstraction handles the domain-specific filtering logic.

---

## 8. Tradeoffs

| Dimension | Haystack | LangChain | LlamaIndex |
|-----------|---------|-----------|-----------|
| Type safety | High (validated at build) | None | Low |
| Pipeline serialization | Native (YAML) | Limited | Limited |
| Component reusability | Excellent | Medium | Medium |
| Production-oriented | Excellent | Good | Good |
| RAG sophistication | Good | Medium | Excellent |
| Agent patterns | Limited | Excellent | Good |
| Community size | Medium | Largest | Large |
| Enterprise features | Good | Limited | Limited |
| Debugging | Good (type errors caught early) | Medium | Medium |
| Streaming | Good | Excellent | Good |

**Haystack vs LangChain:**
Haystack is more opinionated (everything is a pipeline, everything is a component) but safer (type validation). LangChain is more flexible (mix and match anything) but easier to create type mismatches. For production NLP pipelines with clear data flow: Haystack. For general LLM applications with complex chains: LangChain.

---

## 9. When to Use / When NOT to Use

**Use Haystack when:**
- Building production RAG pipelines with well-defined data flow
- Team values type safety and validated pipeline construction
- Need to serialize pipelines to YAML for config-driven deployment
- Document processing pipelines (PDF, HTML, DOCX) with multiple transformation steps
- Enterprise deployment where non-developers need to modify pipeline configuration
- NLP-heavy applications (document classification, extractive QA, information extraction)

**Do NOT use Haystack when:**
- Complex agentic workflows with loops and conditional branching — LangGraph is more appropriate
- Rapid prototyping where flexibility matters more than safety
- Team is already invested in LangChain ecosystem
- Need maximum community integrations
- LLM application is primarily conversational (not document-processing)

---

## 10. Common Pitfalls

**Pitfall 1: Connecting incompatible component outputs/inputs**
Haystack catches type mismatches at `pipeline.connect()` time. But a common mistake: connecting a `str` output to an input expecting `List[str]`:
```python
# Haystack raises PipelineConnectError immediately:
# pipeline.connect("generator.replies", "writer.text")
# where writer expects str, but replies is List[str]
#
# Fix:
pipeline.connect("generator.replies[0]", "writer.text")  # index into list
```

**Pitfall 2: Using InMemoryDocumentStore in production**
`InMemoryDocumentStore` loses all data on restart. Teams test locally with it, deploy to production with the same code, and lose the indexed documents on every redeploy. Use `ChromaDocumentStore`, `OpenSearchDocumentStore`, or `PineconeDocumentStore` for production.

**Pitfall 3: Missing query in multiple pipeline inputs**
When the same query string is needed by multiple components (embedder AND ranker AND prompt_builder), teams must pass it to each separately:
```python
result = pipeline.run({
    "query_embedder": {"text": query},  # for embedding
    "ranker": {"query": query},          # for reranking
    "prompt_builder": {"query": query}   # for prompt formatting
})
# Missing any of these causes the component to receive None and fail silently
```
Mitigation: design a single-input wrapper component that fans out the query to all consumers.

**Pitfall 4: Haystack 1.x to 2.x migration**
Haystack 2.0 is a complete rewrite with breaking changes: component API changed, pipeline definition changed, `Finder` removed, retrieval API changed. Teams with Haystack 1.x code cannot upgrade without rewriting. Check if your team's code is pre-2.0; migration guides exist but the effort is significant.

**Pitfall 5: Retrieval top_k too small**
Default `top_k=1` or `top_k=3` in retrievers is insufficient for diverse corpora. With a reranker: retrieve `top_k=20`, rerank to `top_n=3`. Without a reranker: use `top_k=5-10`. Teams use defaults, get poor answers, assume the LLM is the problem. Tune retrieval first.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| `haystack-ai` | Core framework | `pip install haystack-ai` |
| `haystack-experimental` | Experimental features | Agent components (in development) |
| `chroma-haystack` | Vector store | ChromaDB integration |
| `opensearch-haystack` | Document store | OpenSearch integration |
| `pinecone-haystack` | Vector store | Pinecone integration |
| `instructor-haystack` | Structured output | Instructor integration |
| `deepset Cloud` | Managed deployment | Hosted Haystack execution |

**Version notes:**
- Haystack 1.x: legacy, `Pipeline.add_node()`, `EmbeddingRetriever`, `Generator`
- Haystack 2.0 (Jan 2024): complete rewrite, `@component` protocol, `Pipeline.add_component()`, typed connections
- `haystack-ai` package (new name for 2.x); `farm-haystack` is the 1.x package

---

## 12. Interview Questions with Answers

**Q: What is Haystack and how does its pipeline model work?**
Haystack is a Python LLM framework from deepset built around typed pipeline DAGs. A pipeline is a set of `Component` nodes connected by typed edges. Each component declares its output types with `@component.output_types` and implements `run()` with typed parameters. `Pipeline.connect("source.output_key", "target.input_key")` adds an edge; Haystack validates type compatibility at connection time. Pipelines are executed by calling `pipeline.run({"component_name": {"input_key": value}})`. This model catches integration bugs early (type mismatches at build, not runtime) and enables YAML serialization.

**Q: What is the @component protocol and how do you create a custom component?**
The `@component` decorator marks a class as a Haystack component. The class must: (1) declare output types with `@component.output_types(key=type)` above the `run()` method; (2) implement `run()` with typed parameters; (3) return a dict matching the declared output types. Input types are inferred from `run()` parameter type annotations. Haystack validates that connected component output types match the receiving component's input types at pipeline construction time. Custom components enable: domain-specific processing, external API calls, custom reranking logic, and integration with non-standard data sources.

**Q: How does Haystack handle hybrid retrieval?**
Haystack implements hybrid retrieval by running BM25 and embedding retrievers in parallel, then merging and reranking results. In the pipeline: connect the query to both `InMemoryBM25Retriever` and `InMemoryEmbeddingRetriever`; both outputs connect to `DocumentJoiner`; the joiner deduplicates (by document ID) and merges the lists; the merged list goes to a `TransformersSimilarityRanker` which reranks using a cross-encoder model. The pipeline DAG supports fan-out (one input → multiple components) and fan-in (multiple inputs → one component) natively.

**Q: How does Haystack pipeline serialization work?**
`pipeline.to_dict()` serializes the pipeline to a Python dict (convertible to YAML). The YAML contains: component definitions (class name, init parameters), component connections (source.key → target.key), and pipeline-level metadata. `Pipeline.from_dict(yaml_dict)` reconstructs the pipeline. This enables: (1) version control for pipeline configurations; (2) config-driven deployment (change the pipeline without code changes); (3) non-developer pipeline management (operations team can modify component parameters); (4) A/B testing pipeline variants by loading different YAML configs. Limitation: custom components must be importable in the environment where the pipeline is loaded.

**Q: What document stores does Haystack support and how do you choose?**
Haystack supports: `InMemoryDocumentStore` (development only, no persistence), `ChromaDocumentStore` (open-source, easy setup, moderate scale), `OpenSearchDocumentStore` (full-text + vector, enterprise-scale, Azure/AWS hosted), `PineconeDocumentStore` (managed vector, serverless option), `WeaviateDocumentStore` (multimodal, cloud/self-hosted), `ElasticsearchDocumentStore` (existing ES infrastructure). Selection criteria: (1) Scale — InMemory for dev; Chroma for <1M docs; OpenSearch/Pinecone for >1M docs; (2) Existing infrastructure — OpenSearch if you already run ES; (3) Hybrid retrieval — OpenSearch supports BM25 + vector natively.

**Q: How do you evaluate a Haystack RAG pipeline?**
Haystack provides evaluation components: `FaithfulnessEvaluator` (LLM-as-judge: is answer grounded in context?), `ContextRelevanceEvaluator` (are retrieved docs relevant to the question?), `DocumentMRREvaluator` (mean reciprocal rank of retrieved docs), `DocumentRecallEvaluator` (did we retrieve the ground truth doc?). Build an evaluation pipeline: indexing → retrieval → generation → evaluation. Create ground truth datasets (questions + expected answers + relevant doc IDs). Run evaluation on 100-500 examples; track metrics over time. Integrate with experiment tracking (MLflow, W&B) for comparing pipeline variants.

**Q: How do you implement metadata filtering in a Haystack retrieval pipeline?**
Set `meta` on `Document` objects during indexing. At query time, pass `filters` to the retriever:
```python
result = rag_pipeline.run({
    "retriever": {
        "query": "What is the refund policy?",
        "top_k": 5,
        "filters": {"field": "meta.department", "operator": "==", "value": "legal"}
    }
})
```
Supported operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `in`, `not in`. Combine with `AND`/`OR` operators. The filter is applied by the document store before returning results. Performance: metadata filters are efficient in most backends (Chroma, OpenSearch, Pinecone); they reduce the candidate set before vector similarity computation.

**Q: How does Haystack 2.0 differ from Haystack 1.x?**
Haystack 2.0 is a complete rewrite with breaking changes: (1) Component protocol — `@component` decorator and `run()` method replace `@pipeline.node()` and various base classes; (2) Pipeline construction — `add_component()` + `connect()` replace `add_node()` with node names; (3) Type validation — added at connection time in 2.0; absent in 1.x; (4) Removed `Finder` class — replaced by Pipeline; (5) Package rename — `farm-haystack` (1.x) → `haystack-ai` (2.x); (6) Better async support; (7) Simplified document model. 1.x code requires significant rewriting to migrate to 2.x.

**Q: How do you build a conversational RAG pipeline in Haystack?**
Add `ChatPromptBuilder` instead of `PromptBuilder`, and `OpenAIChatGenerator` instead of `OpenAIGenerator`. Maintain `ChatHistory` externally and pass it to the pipeline on each turn:
```python
from haystack.components.builders import ChatPromptBuilder
from haystack.components.generators.chat import OpenAIChatGenerator
from haystack.dataclasses import ChatMessage

chat_template = """
Previous conversation:
{% for message in chat_history %}
  {{message.role}}: {{message.content}}
{% endfor %}

Context: {% for doc in documents %}{{doc.content}}{% endfor %}

User: {{query}}
Assistant:
"""

# Each turn: retrieve + generate, append result to history
chat_history = []
for user_input in user_inputs:
    result = pipeline.run({
        "query_embedder": {"text": user_input},
        "prompt_builder": {"query": user_input, "chat_history": chat_history}
    })
    answer = result["llm"]["replies"][0].content
    chat_history.append(ChatMessage.from_user(user_input))
    chat_history.append(ChatMessage.from_assistant(answer))
```

**Q: How do you handle PDF documents with tables in Haystack?**
Standard converters (`PDFToTextConverter` using `pypdf`) lose table structure. Better options: (1) `PDFMinerToDocument` (better table extraction than pypdf); (2) `AzureOCRDocumentConverter` (Azure Cognitive Services, handles scanned PDFs); (3) Unstructured.io integration (`UnstructuredFileConverter`) — commercial API that handles tables, headers, and complex layouts. For critical table data (financial documents, regulatory filings): use Unstructured or Azure OCR. After conversion, customize the `DocumentSplitter` to respect table boundaries — don't split in the middle of a table row.

**Q: What is the DocumentJoiner component and when do you use it?**
`DocumentJoiner` merges document lists from multiple retrievers into a single list, with optional deduplication (by document ID). Use it in hybrid retrieval: BM25 retriever produces keyword-matched docs; embedding retriever produces semantically similar docs; joiner merges them, removes duplicates, and passes to a reranker. Configuration: `join_mode="concatenate"` (append all lists) or `join_mode="reciprocal_rank_fusion"` (RRF score combination for better quality hybrid ranking). RRF is generally better than simple concatenation for hybrid retrieval.

**Q: How do you scale Haystack for high-throughput production?**
Scaling approaches: (1) Async pipeline — use `pipeline.run_async()` and `asyncio.gather()` to process multiple requests concurrently; (2) Horizontally scale the API layer — Haystack pipelines are stateless (no internal state between calls); deploy behind a load balancer; (3) Use managed vector stores — Pinecone Serverless or OpenSearch Service scale independently; don't run your own vector DB under high load; (4) Embedding caching — cache query embeddings for repeated questions; reduces OpenAI API calls and latency; (5) Reranker on GPU — if using `TransformersSimilarityRanker`, run on GPU; CPU reranking adds 500ms+ at 100+ documents. Benchmark: Haystack pipeline overhead (excluding LLM calls) is typically <50ms.

---

## 13. Best Practices

1. **Use type annotations in custom components** — Haystack infers input types from `run()` signatures; missing annotations cause runtime failures.
2. **Start with `InMemoryDocumentStore` for development** — switch to persistent store before QA testing.
3. **Always rerank retrieved documents** — raw vector search accuracy is often insufficient; `TransformersSimilarityRanker` or `CohereRanker` significantly improves precision.
4. **Retrieve more than needed, then rerank** — `top_k=20` + rerank to `top_n=3` beats `top_k=3` directly.
5. **Add document metadata during indexing** — source file, date, section, department; enables filtering at query time.
6. **Test pipeline construction before deployment** — `pipeline.run({"component": {"input": dummy_value}})` validates all connections.
7. **Serialize pipelines to YAML** — enables config-driven changes without code deployments.
8. **Use `DocumentSplitter` split_by="sentence"** for conversational content; split_by="word" for technical documentation.
9. **Evaluate retrieval separately from generation** — MRR/Recall metrics tell you if the retrieval component is the bottleneck.
10. **Pin haystack-ai version** — Haystack 2.x is actively developed; breaking changes in minor versions can occur.

---

## 14. Case Study: Enterprise Document Search and Q&A

**Scenario**: A global consulting firm with 200,000 employees needs internal Q&A over 2 million documents: project proposals, engagement letters, methodology guides, and training materials. Requirements: semantic search, access control (users see only their practice area's documents), citation of sources, and <2 second response time.

### Architecture

```
INDEXING PIPELINE (nightly batch):

  [SharePointConnector → TextFileToDocument]
     Metadata: {practice_area, document_type, date, authors, project_id}
         |
  [DocumentSplitter (split_by="sentence", split_length=5, overlap=1)]
         |
  [OpenAIDocumentEmbedder (model="text-embedding-3-large")]
         |
  [DocumentWriter → OpenSearchDocumentStore]
     (2 million documents, updated nightly with new/modified docs)

QUERY PIPELINE (per request):

  user_query + user.practice_areas (from auth token)
         |
  [OpenAITextEmbedder]        [MetadataFilter (practice_area IN user.practice_areas)]
         |                             |
  [OpenSearchEmbeddingRetriever (top_k=20, filters=user_filters)]
         |
  [TransformersSimilarityRanker (top_k=5)]
         |
  [PromptBuilder + citation template]
         |
  [OpenAIChatGenerator (gpt-4o)]
         |
  Response + 5 source citations (document title, section, URL)
```

### Custom Access Control Component

```python
from haystack import component, Document
from haystack.core.component.types import List

@component
class PracticeAreaFilter:
    """Filter documents to user's allowed practice areas."""

    @component.output_types(
        filters=dict
    )
    def run(self, user_practice_areas: list[str]) -> dict:
        """Generate Haystack metadata filters for user's practice areas."""
        return {
            "filters": {
                "operator": "OR",
                "conditions": [
                    {"field": "meta.practice_area", "operator": "==", "value": area}
                    for area in user_practice_areas
                ]
            }
        }

pipeline.add_component("access_control", PracticeAreaFilter())
pipeline.connect("access_control.filters", "retriever.filters")
```

### Results

| Metric | Before (SharePoint search) | After (Haystack RAG) |
|--------|---------------------------|---------------------|
| Search precision | 51% relevant in top 5 | 84% relevant in top 5 |
| Answer quality | N/A (no Q&A) | 4.2/5 (user rating) |
| Response latency P50 | 0.3s (keyword search) | 1.4s (embedding + rerank + generate) |
| Response latency P99 | 0.8s | 3.1s |
| Access control violations | N/A | 0 (filter at retrieval time) |
| Citations provided | N/A | 100% (5 source docs per answer) |
| Monthly AI cost | $0 | $2,800 (250K queries × avg $0.011) |
