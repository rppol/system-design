# Multimodal RAG

## 1. Concept Overview

Multimodal RAG extends the standard text-based RAG pipeline to handle documents that contain non-text content: images, charts, tables, diagrams, equations, and slides. Enterprise documents are rarely purely textual — PDFs contain embedded charts, research papers include figures, presentations are image-heavy, and technical documentation mixes diagrams with prose.

Standard RAG loses all information in non-text content. Multimodal RAG preserves this information using two primary strategies: (1) convert non-text elements to rich text descriptions using vision-LLMs or OCR, then embed those descriptions as text; (2) embed images directly using multimodal embedding models (CLIP, SigLIP) and retrieve them using cross-modal queries.

---

## Intuition

> **One-line analogy**: Multimodal RAG teaches the library catalog to index the pictures, diagrams, and tables in books, not just the text.

**Mental model**: A financial analyst's report is 40% charts, 20% tables, and 40% text. Standard RAG indexes only the text portion; the charts showing revenue trends, the tables comparing quarterly metrics — all lost. Multimodal RAG extracts each chart and table, generates a rich text description using a vision LLM ("Bar chart showing Q1-Q4 2024 revenue: Q1 $2.1B, Q2 $2.4B, Q3 $2.8B, Q4 $3.2B, 53% YoY growth"), and includes those descriptions in the searchable index.

**Why it matters**: Critical information is often in non-text elements. A document saying "See Figure 3 for the performance comparison" is useless if Figure 3 isn't indexed. Multimodal RAG recovers this information and makes it retrievable.

**Key insight**: Vision LLMs (GPT-4o, Claude 3.5, Gemini 1.5 Pro) are now capable of generating accurate, detailed descriptions of charts, tables, and diagrams — making vision-to-text conversion practical for production indexing pipelines.

---

## 2. Core Principles

- **Multiple modalities require modality-specific indexing**: Text, images, tables, and code each have different optimal indexing strategies.
- **Cross-modal alignment**: A text query must be able to retrieve a relevant image — achieved either by embedding both in a shared space (CLIP) or by describing images as text.
- **Extraction quality is the bottleneck**: A vision LLM that generates inaccurate chart descriptions produces a misleading index.
- **Retrieval and generation are both multimodal**: Retrieved images must be passed to a vision-capable generation model.
- **Resolution matters**: Low-resolution images produce poor CLIP embeddings and unreliable vision LLM descriptions; maintain minimum 300 DPI for document images.

---

## 3. How It Works — Detailed Mechanics

### 3.1 Document Parsing and Element Extraction

```
PDF processing pipeline:
  Tool: PyMuPDF, pdfplumber, Adobe PDF Services API, or Unstructured.io

  Extract:
    Text blocks → standard text chunking
    Tables → CSV/Markdown representation
    Figures/images → save as PNG at 150+ DPI
    Page layout → spatial coordinates of each element
    Captions → associate "Figure 3: Revenue comparison" text with image

  HTML/web:
    BeautifulSoup + Playwright
    Extract <img> tags with alt text and surrounding context

  PPTX:
    python-pptx
    Each slide → text + image, slide number, section
```

### 3.2 Strategy 1: Vision LLM Description (Text-Then-Embed)

Convert visual elements to rich text descriptions using a vision LLM, then embed as text:

```python
from openai import OpenAI
import base64

def describe_image(image_path: str, context: str = "") -> str:
    """Generate a detailed text description of an image using GPT-4o."""
    with open(image_path, "rb") as f:
        image_data = base64.b64encode(f.read()).decode()

    client = OpenAI()
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[{
            "role": "user",
            "content": [
                {
                    "type": "image_url",
                    "image_url": {"url": f"data:image/png;base64,{image_data}"}
                },
                {
                    "type": "text",
                    "text": f"""Describe this image in detail for a search index.
                    Include: type of visualization, all visible data values,
                    axis labels, legend, title, key trends, and conclusions.
                    Document context: {context}

                    If this is a chart: include all numeric values you can read.
                    If this is a table: transcribe the data in markdown format.
                    If this is a diagram: describe the relationships shown."""
                }
            ]
        }],
        max_tokens=500
    )
    return response.choices[0].message.content

# Result stored as text chunk with metadata: {source_pdf, page, element_type: "figure"}
# Embedded using same text embedding model as regular chunks
```

Table extraction (direct, no vision needed):
```python
import pdfplumber

def extract_tables(pdf_path: str) -> list[dict]:
    tables = []
    with pdfplumber.open(pdf_path) as pdf:
        for page_num, page in enumerate(pdf.pages):
            for table in page.extract_tables():
                # Convert to markdown
                headers = table[0]
                rows = table[1:]
                md = " | ".join(headers) + "\n"
                md += " | ".join(["---"] * len(headers)) + "\n"
                for row in rows:
                    md += " | ".join([str(c) for c in row]) + "\n"
                tables.append({
                    "content": md,
                    "page": page_num,
                    "element_type": "table"
                })
    return tables
```

### 3.3 Strategy 2: Direct Image Embedding (CLIP/SigLIP)

Embed images directly using a multimodal embedding model; store image embeddings alongside text embeddings:

```python
from transformers import CLIPProcessor, CLIPModel
import torch
from PIL import Image

model = CLIPModel.from_pretrained("openai/clip-vit-large-patch14")
processor = CLIPProcessor.from_pretrained("openai/clip-vit-large-patch14")

def embed_image(image_path: str) -> list[float]:
    image = Image.open(image_path)
    inputs = processor(images=image, return_tensors="pt")
    with torch.no_grad():
        features = model.get_image_features(**inputs)
    return features[0].numpy().tolist()  # 768-dim vector

def embed_text_for_image_retrieval(text: str) -> list[float]:
    """CLIP text embedding — same space as image embedding."""
    inputs = processor(text=[text], return_tensors="pt", padding=True)
    with torch.no_grad():
        features = model.get_text_features(**inputs)
    return features[0].numpy().tolist()

# Store image embeddings in same vector DB as text embeddings
# At query time: embed query with CLIP text encoder, search image collection
```

CLIP alignment gap: CLIP text and image embeddings are in the same vector space but not perfectly aligned — a text query about "revenue growth" may not retrieve a revenue chart as reliably as semantic text search retrieves a text passage. Solution: dual retrieval (text for text chunks, CLIP for images) with a reranker that scores across both modalities.

### Two Ways to Index an Image: Text Space vs Joint Space

The two strategies differ in *where the image lands* in vector space. Strategy 1 converts the
image to a text caption first, so it lives in the ordinary text-embedding space beside normal
chunks. Strategy 2 embeds pixels directly into CLIP's joint image-text space, a separate index.

```
  Strategy 1  (Vision LLM → text-then-embed)
     image ─► GPT-4o caption ─► text embedder ─┐
     text  ───────────────────► text embedder ─┴─► ONE shared text space, one reranker
                                                   (accurate, reuses text infra; ~1 VLM call/img)

  Strategy 2  (Direct image embedding)
     image ─► CLIP image encoder ─► image vector ─┐
     query ─► CLIP text encoder  ─► text vector  ─┴─► CLIP joint space (separate index)
                                                      (cheap + fast; text↔image alignment gap)
```

The tradeoff is direct: Strategy 1 buys accuracy and infra reuse at the cost of a vision-LLM
call per image; Strategy 2 buys throughput and low cost but inherits the alignment gap above.

### 3.4 Generation with Vision LLMs

Retrieved context must include both text and images; the generation LLM must be vision-capable:

```python
def generate_multimodal_answer(
    query: str,
    text_chunks: list[str],
    image_paths: list[str],
    llm_client
) -> str:
    content = []

    # Add text context
    for chunk in text_chunks:
        content.append({"type": "text", "text": f"[Document excerpt]: {chunk}"})

    # Add retrieved images
    for img_path in image_paths:
        with open(img_path, "rb") as f:
            img_data = base64.b64encode(f.read()).decode()
        content.append({
            "type": "image_url",
            "image_url": {"url": f"data:image/png;base64,{img_data}"}
        })

    # Add query
    content.append({"type": "text", "text": f"Question: {query}"})

    response = llm_client.chat.completions.create(
        model="gpt-4o",  # or claude-3-5-sonnet, gemini-1.5-pro
        messages=[
            {"role": "system", "content": "Answer based on the provided documents and images. Cite specific figures when referencing visual data."},
            {"role": "user", "content": content}
        ]
    )
    return response.choices[0].message.content
```

---

## 4. Architecture Diagram

### Multimodal Indexing Pipeline
```
Source Documents (PDF, PPTX, HTML)
    |
    v
[Document Parser]  (Unstructured.io, PyMuPDF, python-pptx)
    |
    +-----------> Text blocks
    |                 |
    |                 v
    |            [Text chunking → Text embeddings → Vector DB]
    |
    +-----------> Tables
    |                 |
    |                 v
    |            [Markdown conversion → Text embeddings → Vector DB]
    |
    +-----------> Figures/Charts/Diagrams
                      |
                      +----> [Vision LLM description] → Text embeddings → Vector DB
                      |
                      +----> [CLIP image embeddings]  → Image Vector DB
                      |
                      +----> [Original image stored]  → Blob Storage (S3/GCS)
                                                          (for retrieval + generation)
```

### Multimodal Query Pipeline
```
User Query (text)
    |
    +----> [Text embedding]  ---> Text Vector DB  ---> top-K text/table chunks
    |
    +----> [CLIP text embed] ---> Image Vector DB ---> top-K relevant images
    |                                 (image descriptions also in text DB)
    v
[Merge + Rerank]
    Combine text chunks + image results
    Cross-encoder reranker (text-only; scores image descriptions)
    |
    v
[Context Assembly]
    Text chunks + retrieved original images (from blob storage)
    |
    v
[Vision-LLM Generation]  (GPT-4o / Claude 3.5 Sonnet / Gemini 1.5 Pro)
    Sees both text context AND images
    |
    v
Answer with source citations (document, page, figure number)
```

---

## 5. Real-World Examples

### NotebookLM (Google)
- Processes PDFs including figures and tables
- Generates podcast-style audio from multimodal content
- Uses Gemini multimodal capabilities throughout the pipeline

### Unstructured.io Enterprise
- Document processing API that extracts tables, figures, headers, lists as separate elements
- Used in production by enterprises indexing mixed-content PDFs
- Integrates with all major vector DBs for element-level indexing

### GPT-4o with File Attachments
- OpenAI's file search tool (formerly Retrieval) uses vision capabilities for PDF processing
- Extracts and indexes table and chart content; vision LLM generates descriptions
- Powers enterprise knowledge bases with mixed-content documents

---

## 6. Tradeoffs

| Approach | Retrieval Accuracy | Index Cost | Visual Detail Preserved | Requires Vision LLM |
|----------|-------------------|------------|------------------------|---------------------|
| OCR only | Poor (charts lost) | Low | None | No |
| Caption-only | Moderate | Low | Partial | No |
| Vision LLM description | High | High (per image call) | High | Yes (indexing) |
| CLIP direct embedding | Moderate | Low | Yes (native) | No |
| Vision LLM + CLIP combined | Best | High | Best | Yes |

| Vision Model | Quality | Cost | Latency |
|-------------|---------|------|---------|
| GPT-4o | Excellent | $5/1K img tokens | ~2s |
| Claude 3.5 Sonnet | Excellent | $3/1K img tokens | ~2s |
| Gemini 1.5 Pro | Excellent | $3.5/1M tokens | ~2s |
| LLaVA 1.6 (self-hosted) | Good | GPU cost | ~1s |

---

## 7. When to Use / When NOT to Use

### Use Multimodal RAG When:
- Documents contain significant visual information (charts, diagrams, tables, slides)
- Users ask about trends visible in charts ("Show me revenue growth across quarters")
- Documents are technical with diagrams essential to understanding
- Loss of visual content causes critical information gaps

### Use Text-Only RAG When:
- Documents are primarily prose with minimal visual content
- Budget prohibits vision LLM calls per image during indexing
- Latency requirements are strict (vision LLM descriptions add significant indexing time)
- Visual content is decorative, not informational

---

## 8. Common Pitfalls

**1. Low-resolution image extraction**
Images extracted at 72 DPI from PDFs are too blurry for vision LLM description or CLIP embedding.
Fix: Extract at minimum 150 DPI, preferably 300 DPI. Use vector graphics export where PDF supports it.

**2. Context disconnection — image without surrounding text**
A chart is indexed with its vision LLM description but without the surrounding document context that explains what the chart represents.
Fix: Include surrounding text (±200 tokens) as metadata context when generating vision LLM descriptions: "Describe this image given the surrounding document context: [text before and after the figure]."

**3. CLIP text-image alignment gap for domain-specific content**
Standard CLIP models are trained on general web images. Domain-specific charts, technical diagrams, or specialized document types may not align well in CLIP's embedding space.
Fix: Use domain-specific fine-tuned CLIP variants, or rely primarily on vision LLM text descriptions (which embed in the standard text space) for high-precision domain retrieval.

**4. Table extraction failure with complex layouts**
Merged cells, nested headers, and multi-page tables cause extraction errors. An incorrectly extracted table produces worse context than no table.
Fix: Validate table extraction by comparing cell count and structure against the source PDF. Use multiple extraction methods (pdfplumber + Camelot + LLM-based) and select the best result.

**5. Passing too many images to the generation LLM**
A context with 5 large images consumes thousands of tokens, reducing the number of text chunks that fit and increasing cost significantly.
Fix: Limit retrieved images to 2-3 per query. Retrieve image descriptions as text first; only retrieve original images when the description suggests high relevance.

**6. No fallback for vision LLM description failures**
Vision LLM occasionally fails to accurately describe complex charts (overlapping bars, dense scatter plots).
Fix: Include image quality checks; fall back to OCR text for images where vision LLM confidence is low; log description quality metrics.

---

## 9. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Unstructured.io** | Document parsing (text, tables, images) | Best-in-class enterprise document parser |
| **PyMuPDF (fitz)** | PDF image/text extraction | Fast, reliable; good Python API |
| **pdfplumber** | PDF table extraction | Best Python tool for precise table extraction |
| **CLIP / SigLIP** | Image embedding | OpenAI CLIP (ViT-L); Google SigLIP for better quality |
| **GPT-4o** | Vision LLM descriptions | Best quality for chart/diagram description |
| **Claude 3.5 Sonnet** | Vision LLM descriptions | Strong quality; good at technical diagrams |
| **LLaVA 1.6 / InternVL2** | Open-source vision LLM | Self-hosted; good for cost-sensitive indexing |
| **Weaviate multi2vec** | Multimodal vector DB | Native multi-modal (text + image) in one index |
| **LlamaIndex MultiModalVectorStore** | Multimodal RAG framework | Handles multi-modal retrieval and routing |

---

## 10. Interview Questions with Answers

**Q: What are the two main strategies for indexing images in a RAG system?**
A: The two strategies are vision-to-text conversion and direct image embedding. In vision-to-text: a vision LLM (GPT-4o, Claude 3.5) generates a detailed text description of each image, which is then embedded using a standard text embedding model and stored alongside text chunks. This leverages the full power of semantic text search and works with existing vector DBs. In direct image embedding: a multimodal embedding model (CLIP, SigLIP) encodes images directly into a vector space aligned with text embeddings, allowing cross-modal retrieval. Vision-to-text produces higher-quality descriptions for complex charts; CLIP is cheaper and faster. In production, combining both is most effective.

**Q: What is CLIP and how does cross-modal retrieval work?**
A: CLIP (Contrastive Language-Image Pretraining, OpenAI 2021) trains a text encoder and image encoder together using contrastive learning on 400M image-caption pairs. The result: text and image encoders produce vectors in the same high-dimensional space — "a photo of a dog" embeds close to an image of a dog. Cross-modal retrieval embeds a text query using CLIP's text encoder, then searches the image embedding collection for nearest neighbors. The alignment is imperfect for domain-specific content (scientific diagrams, financial charts) not well-represented in CLIP's training data. SigLIP (Google, 2023) uses sigmoid loss instead of contrastive softmax and achieves better alignment quality, particularly for web-scale images.

**Q: How do you handle table extraction from PDFs?**
A: Tables in PDFs are stored as positioned text elements (no inherent structure). Extraction strategies in order of quality: (1) Lattice method (pdfplumber, Camelot): infers table boundaries from visible grid lines; works well for bordered tables. (2) Stream method: infers tables from text whitespace alignment; works for unbordered tables. (3) Vision LLM: send the PDF page as an image to a vision LLM and ask it to extract the table as markdown; handles complex layouts including merged cells and multi-column headers. For production: use lattice/stream first; fall back to vision LLM for pages where structured extraction fails. Convert extracted tables to markdown and embed as text.

**Q: Why is surrounding context important when generating vision LLM descriptions for images?**
A: An isolated chart image often lacks the context needed for an accurate description. The same bar chart could be about revenue, user counts, or temperature measurements — the chart itself may not specify. Surrounding document text provides: the chart title's full context, the variable being measured, the time period, the units, and the document's analytical conclusion about the chart. Including ±200 tokens of surrounding text in the vision LLM description prompt produces descriptions that are semantically accurate and contain the right domain vocabulary for retrieval, rather than generic descriptions that don't match user queries.

**Q: What are the limitations of CLIP for domain-specific document images?**
A: CLIP was trained on general web images — product photos, news images, general photographs. It has poor alignment for: (1) Scientific/technical diagrams — circuit diagrams, molecular structures, architectural drawings are rare in CLIP's training data; (2) Financial charts — specific chart types (candlestick, waterfall) may not align well with their text descriptions; (3) Medical imaging — CLIP was not trained on X-rays, MRIs, histology slides. For these domains, alternatives include: domain-specific CLIP fine-tuning (train on domain image-caption pairs), vision LLM description fallback (convert to text, then use text embeddings), or BiomedCLIP/ChemCLIP for specific scientific domains.

**Q: How do you handle the cost of vision LLM calls during indexing?**
A: At scale, vision LLM calls dominate indexing cost. Mitigation strategies: (1) Selective processing — only process images above a minimum size threshold (skip icons, logos under 100×100px); (2) Deduplication — identical or near-identical images across documents should be described only once; (3) Cheaper models for simple content — use GPT-4o-mini for simple tables and charts, reserve GPT-4o for complex diagrams; (4) Batch processing with rate limiting — process images in parallel with controlled concurrency; (5) Caching — cache descriptions for frequently re-indexed documents. For a 10,000-page document set with 3 images per page: 30,000 vision LLM calls at $5/1K image tokens = $150. Plan for this cost.

**Q: How do you pass retrieved images to the generation LLM without exceeding context limits?**
A: Images consume significant context window tokens. GPT-4o prices an image at 85-2048 tokens depending on resolution (detail="low" → 85 tokens; detail="high" → up to 2048). Strategies: (1) Limit retrieved images to 2-3 per query; prefer image descriptions as text when confidence is high; (2) Use detail="low" for retrieved images unless high resolution is critical; (3) Summarize image content (use the stored vision LLM description as context) and only pass the original image when the description alone is insufficient; (4) Prioritize by image relevance score — only retrieve images when the retriever confidence exceeds a threshold.

**Q: How would you evaluate multimodal RAG quality?**
A: Evaluation requires modality-specific metrics plus end-to-end quality. For image description quality: compare vision LLM descriptions against human annotations on 100 sampled images using BERTScore and human ratings; check that key data values (numbers, labels) are correctly extracted. For retrieval quality: build a test set with (text query, expected image ID) pairs; measure retrieval recall@K for cross-modal queries. For end-to-end quality: questions that require chart interpretation ("What was the highest revenue quarter?"); verify that answers correctly read the chart data. Track image-answer attribution: for each image-based answer, verify the cited figure is the correct source.

**Q: What document formats present the most challenges for multimodal RAG?**
A: Scanned PDFs (images of scans, no native text layer) require OCR before any processing — quality depends on scan resolution and OCR accuracy. PowerPoint with complex animation: animated elements may not export correctly to static images. Technical PDFs with vector graphics: line diagrams and schematics in SVG/PDF vector format may render poorly when rasterized. Multi-column academic papers: text flow extraction often incorrectly merges columns. Tables with merged cells: extraction tools frequently misinterpret spanning cells. Mitigation: invest in robust document parsing (Unstructured.io handles most of these) and build a validation layer that flags extraction failures for manual review.

**Q: What is the difference between Unstructured.io's "fast," "hi_res," and "ocr_only" modes?**
A: These are document processing pipeline modes trading accuracy for speed. "fast" mode uses direct text extraction (pdfminer) — fastest but misses layout information and can't handle scanned PDFs; suitable for text-heavy documents with simple layouts. "hi_res" mode renders each page as a high-resolution image and uses a document layout analysis model (detectron2) to identify text, tables, figures, headers before extracting each element type with specialized processors — most accurate, especially for tables and mixed-layout documents; 5-10× slower than fast mode. "ocr_only" applies OCR to every page regardless of whether it has a text layer — useful for scanned documents or when native text extraction produces garbled results.

**Q: How should you handle tables in multimodal RAG — embedded text extraction, text-to-SQL, or embedding the table directly?**
A: The right strategy depends on table complexity and query type. For simple lookup tables (headers + rows, no merged cells), extract to markdown and embed as text — standard text retrieval handles these well. For structured relational tables where users ask aggregation or filter queries ("which line items exceed $1M?"), convert to SQL schema or Pandas DataFrame and use a code-interpreter or text-to-SQL approach rather than embedding; this enables exact computation rather than approximate retrieval. For complex multi-row headers or transposed tables, use a vision LLM to extract the table structure and convert to normalized markdown. The key principle: never embed a table that requires computation — retrieval-based approaches return the whole table as context and let the LLM approximate the answer; text-to-SQL gives exact results. In practice, most enterprise document pipelines use markdown extraction for tables under 50 rows and text-to-SQL for large structured tables from databases.

**Q: How does image description quality affect downstream retrieval accuracy, and how do you measure and improve it?**
A: Image description quality is the primary bottleneck in vision-to-text multimodal RAG pipelines. A poor description (missing key data values, wrong chart type, missing axis labels) produces a text chunk that does not match user queries about that image, resulting in retrieval failure. Measurement: build a 100-image test set with manually verified ground-truth descriptions; score generated descriptions on (a) numeric accuracy — are the key data values present and correct? (b) semantic coverage — do the descriptions mention the key terms a user would use to query for this image? Use BERTScore for semantic similarity and exact-match for numeric values. Improvement strategies: add surrounding document context to the vision LLM prompt; use a multi-turn prompt that first asks "what type of chart is this?" then "list all data values visible"; for charts specifically, GPT-4o's detail="high" mode significantly outperforms detail="low" for reading axis values.

**Q: What is the cost of VLM-based document processing at scale, and how do you budget for it?**
A: VLM processing costs scale with the number of images in your corpus. GPT-4o charges approximately 85 tokens for a low-detail image and 2048 tokens for a high-detail image. At 10,000 pages with an average of 3 images per page (30,000 images), using detail="high": 30,000 × 2048 tokens × $5/1M tokens = $307. For 100,000 pages: ~$3,070 one-time indexing cost. Ongoing incremental costs apply when new documents are added. Cost reduction strategies: (1) use detail="low" for simple charts where data values are large and readable; (2) use a cheaper vision model (LLaVA 1.6 self-hosted, or Claude Haiku) for simple tables; (3) skip images below a minimum area threshold (icons, logos); (4) cache descriptions — if the same image appears in multiple documents (e.g., a standard company logo), describe it once. Benchmark the quality-cost tradeoff before committing to a specific model.

**Q: How do you evaluate multimodal retrieval accuracy specifically for cross-modal queries?**
A: Cross-modal retrieval accuracy — a text query successfully retrieving a relevant image — requires a dedicated evaluation protocol. Build a test set of (text query, expected image ID) pairs: 200-300 pairs covering chart queries ("which quarter had the highest revenue?"), diagram queries ("show the system architecture"), and table queries ("find the pricing comparison table"). Measure Recall@K: what fraction of expected images appear in the top-K retrieved results? Separate metrics for the CLIP path (direct image embedding retrieval) and the vision-LLM-description path (text embedding of descriptions). In practice, the description path typically achieves Recall@10 of 70-85% for well-described charts; the CLIP path achieves 50-70% for domain-specific technical diagrams. Combine both paths and re-rank to achieve best overall recall.

**Q: What chunking strategies work best for mixed-content documents containing both text and visual elements?**
A: Standard token-based chunking destroys the semantic coherence of mixed-content documents by splitting text away from its associated figures and tables. Element-aware chunking is essential: (1) treat each extracted element (text block, table, figure) as a unit with its own chunk boundary — never split a table across two chunks; (2) preserve element-context linkage — each figure chunk carries metadata pointing to its source page, surrounding text (±200 tokens), and caption; (3) use layout-aware chunking that respects document structure (sections, subsections) as natural boundaries. For multi-column documents, parse columns independently before chunking to avoid cross-column text merging. The practical implementation: use Unstructured.io's hi_res mode to extract elements with layout coordinates, then apply element-type-aware chunking rules (text → token-based, tables → whole-table, figures → single chunk + description chunk pair).

---

## 12. Best Practices

1. **Extract at high resolution** — minimum 150 DPI, 300 DPI for documents with dense tables or small text; poor resolution produces poor descriptions and embeddings.
2. **Include surrounding context in vision LLM prompts** — ±200 tokens around each image dramatically improves description accuracy.
3. **Build separate text and image retrieval paths** — dual retrieval (text search + CLIP/description search) then merge; single unified retrieval misses cross-modal signals.
4. **Store original images in blob storage** — always preserve the original image even if you have a text description; the generation LLM benefits from seeing the actual image.
5. **Validate extraction quality** — automated checks: does the description contain numeric values consistent with nearby text? Does the table have the expected number of rows/columns?
6. **Limit images per generation call** — cap at 2-3 images per query; prefer text descriptions for routine queries, original images only for queries that explicitly need visual detail.
7. **Benchmark vision LLM options** — accuracy and cost vary significantly; evaluate GPT-4o, Claude 3.5, and Gemini 1.5 Pro on your specific document types before committing.

---

## 13. Case Study: Multimodal RAG for Manufacturing Equipment Maintenance

**Problem Statement**: A heavy equipment manufacturer with 1,200 field technicians maintains a knowledge base of 4,500 technical manuals covering 380 equipment models. These manuals are 45% text, 30% engineering diagrams (exploded views, wiring schematics, hydraulic circuits), 15% annotated photographs of equipment assemblies, and 10% troubleshooting flowcharts. The company also has a library of 120K equipment failure photographs tagged by model and component, plus 800 video tutorials with transcripts. The existing text-only RAG system indexed prose sections only, losing all visual context. When a technician queried "hydraulic pump seal replacement procedure for Model HX-450," the system returned text steps but missed the critical exploded-view diagram showing seal orientation and the torque specification table embedded in Figure 12. Field surveys revealed that 40% of maintenance queries required visual context that text-only RAG could not provide, resulting in unnecessary escalations to senior technicians (averaging 2.5 hours per escalation) and extended mean-time-to-repair (MTTR) of 4.2 hours per incident.

**Architecture Overview**:
```
Document Sources
    |
    +-- Technical manuals (4,500 PDFs, avg 180 pages each)
    +-- Equipment failure photos (120K images, tagged by model/component)
    +-- Video tutorials (800 videos → keyframe extraction + transcripts)
                                        |
                                        v
                              [Document Parser: Unstructured.io hi_res mode]
                                Render at 300 DPI for diagram clarity
                                Layout analysis: detectron2 model
                                        |
                    +-------------------+-------------------+
                    |                   |                   |
              [Text blocks]      [Tables]           [Figures/Diagrams]
                    |                   |                   |
                    v                   v                   v
          [Section-aware          [Markdown             [Element classifier]
           chunking]               conversion]           CLIP zero-shot:
          800 tokens,             Preserve headers,      exploded-view, wiring
          respect section         units, torque          schematic, flowchart,
          boundaries              spec values            photograph, data chart
                    |                   |                   |
                    |                   |         +--------+--------+
                    |                   |         |                 |
                    |                   |   [Vision LLM            [CLIP ViT-L/14
                    |                   |    Description]           Image Embedding]
                    |                   |    GPT-4o detail=high     768-dim vectors
                    |                   |    for schematics;              |
                    |                   |    GPT-4o-mini for              |
                    |                   |    simple photos                |
                    |                   |         |                       |
                    v                   v         v                       v
              [Text Embed]     [Text Embed]   [Text Embed]        [Image Vector DB]
              text-embed-3     text-embed-3   text-embed-3        Weaviate multi2vec
              -large           -large         -large
                    |                 |              |                    |
                    +-----------------+--------------+                   |
                                     |                                  |
                              [Pinecone: Text Index]             [Weaviate: Image]
                                     |                                  |
                                     +----------------------------------+
                                                    |
                                          [S3 Blob Storage]
                                    Original images with metadata
                                    (manual_id, page, figure_num, model)
                                                    |
                                              Query Time
                                                    |
                                     [Equipment Model Detection]
                                     Extract model number from query
                                     for metadata filtering
                                                    |
                                          [Dual Retrieval]
                                Text query -> text embed -> Pinecone (top-10)
                                Text query -> CLIP text embed -> Weaviate (top-5)
                                                    |
                                          [Merge + Rerank]
                                Cross-encoder reranks text candidates
                                CLIP scores rank image candidates
                                Weighted merge: text 0.6, image 0.4
                                                    |
                                          [Figure-Reference Linking]
                                If text chunk references "See Figure 12",
                                auto-include Figure 12 from S3
                                                    |
                                          [Context Assembly]
                                Top-5 text chunks + top-2 images
                                (originals fetched from S3)
                                                    |
                                          [VLM Generation: GPT-4o]
                                Sees text + original diagrams/photos
                                Generates step-by-step answer with
                                figure references and spec callouts
                                                    |
                                          Answer with visual citations
                                "See attached Figure 12 for seal orientation.
                                 Torque to 45 Nm per specification table."
```

**Key Design Decisions**:
1. Element classification before vision LLM processing — not all images need the same treatment. Exploded-view diagrams and wiring schematics require GPT-4o with detail="high" (2048 tokens per image, ~$0.01 each) for accurate part number and torque specification extraction. Simple equipment photographs use GPT-4o-mini at detail="low" (~$0.001 each). CLIP zero-shot classification routes images to the appropriate model. This classification reduced vision LLM indexing cost by 65% compared to uniform GPT-4o high-detail processing.
2. 300 DPI extraction for all diagrams — initial prototype at 150 DPI produced vision LLM descriptions that missed 30% of part numbers and torque specifications in dense engineering drawings. Upgrading to 300 DPI increased storage 4x but improved specification extraction accuracy from 68% to 94%.
3. Dual retrieval paths with figure-reference linking — CLIP retrieval alone achieved only 52% recall on domain-specific engineering diagrams (hydraulic schematics are poorly represented in CLIP's web-trained embedding space). Vision LLM text descriptions embedded in the text index achieved 78% recall. Combining both with weighted merge achieved 87%. Additionally, when a retrieved text chunk references "See Figure 12," the linked figure is automatically included in context assembly, ensuring the technician always sees referenced diagrams.
4. Section-aware chunking preserving document structure — text chunks respect manual section boundaries (e.g., "3.2 Hydraulic Pump Disassembly" stays as one chunk even if slightly over 800 tokens) rather than splitting mid-procedure. Each chunk carries metadata linking to figures within that section.

**Implementation**:
```python
# Element classification for cost-optimized vision LLM processing
def classify_and_describe_image(
    image_path: str, context: str, metadata: dict
) -> dict:
    """Route images to appropriate vision model based on content type."""
    # Fast classification using CLIP zero-shot
    labels = [
        "exploded view diagram", "wiring schematic", "hydraulic circuit",
        "troubleshooting flowchart", "equipment photograph",
        "simple icon or logo", "data table screenshot"
    ]
    predicted_type = clip_zero_shot_classify(image_path, labels)

    if predicted_type in ("simple icon or logo",):
        return {"skip": True, "reason": "decorative"}

    if predicted_type in (
        "exploded view diagram", "wiring schematic", "hydraulic circuit"
    ):
        # High-detail processing for technical diagrams
        description = vision_llm_describe(
            image_path, context,
            model="gpt-4o",
            detail="high",
            prompt_suffix=(
                "Extract ALL part numbers, torque specifications, "
                "measurement values, and component labels visible. "
                "Describe spatial relationships between components. "
                "List any safety warnings or critical notes."
            )
        )
    elif predicted_type == "troubleshooting flowchart":
        description = vision_llm_describe(
            image_path, context,
            model="gpt-4o",
            detail="high",
            prompt_suffix=(
                "Transcribe the flowchart: list each decision point, "
                "its yes/no branches, and the action at each terminal node."
            )
        )
    else:
        # Standard processing for equipment photos
        description = vision_llm_describe(
            image_path, context,
            model="gpt-4o-mini",
            detail="low",
            prompt_suffix=(
                "Describe the equipment component shown, "
                "any visible damage or wear patterns, and all labels."
            )
        )

    return {
        "description": description,
        "image_type": predicted_type,
        "equipment_model": metadata.get("model"),
        "figure_number": metadata.get("figure_number"),
        "source_manual": metadata.get("manual_id"),
        "page": metadata.get("page")
    }


# Dual retrieval with figure-reference linking
def retrieve_with_images(query: str, equipment_model: str = None):
    metadata_filter = (
        {"equipment_model": equipment_model} if equipment_model else {}
    )

    # Text path
    text_results = pinecone.query(
        vector=embed_text(query), top_k=10, filter=metadata_filter
    )

    # Image path (CLIP cross-modal)
    image_results = weaviate.query(
        vector=clip_embed_text(query), top_k=5, filter=metadata_filter
    )

    # Collect linked figures from retrieved text chunks
    linked_figure_ids = set()
    for chunk in text_results:
        if chunk.metadata.get("linked_figures"):
            linked_figure_ids.update(chunk.metadata["linked_figures"])

    # Fetch linked figures from S3 (ensures "See Figure 12" references resolve)
    linked_images = [fetch_image_from_s3(fig_id) for fig_id in linked_figure_ids]

    # Merge and rerank
    top_text = rerank_cross_encoder(query, text_results, top_k=5)
    all_images = deduplicate_images(image_results + linked_images)[:3]

    return top_text, all_images
```

**Results**:

| Metric | Text-Only RAG | Multimodal RAG |
|--------|--------------|----------------|
| Query resolution without escalation | 54% | 86% (-60% escalations) |
| Mean time to repair (MTTR) | 4.2 hours | 2.7 hours (-35%) |
| Technician satisfaction (1-5 survey) | 2.8 | 4.4 |
| Correct part identification from query | 61% | 89% |
| Specification value extraction accuracy | N/A | 94% (at 300 DPI) |
| One-time indexing cost | $380 | $4,200 |
| Indexing time (8 parallel workers) | 8 hours | 52 hours |
| Query latency (p50) | 0.9s | 2.4s |
| Query latency (p95) | 1.8s | 4.1s |
| Incremental cost per new manual | ~$0.10 | ~$1.50 |

**Tradeoffs**: The $4,200 one-time indexing cost is justified against operational savings: with 1,200 technicians averaging 8 queries/day and a 35% MTTR reduction, the system saves approximately 1,400 technician-hours per month. The 52-hour initial indexing required batched weekend processing across 8 parallel workers; incremental indexing for new manuals (~200/year) costs $1-2 per manual. The 2.4s query latency (vs. 0.9s text-only) is acceptable for field technicians who previously spent 10-15 minutes searching physical manuals. The CLIP retrieval path underperforms on domain-specific engineering diagrams but catches equipment failure photographs that vision LLM descriptions sometimes inadequately describe — removing the CLIP path drops overall image recall from 87% to 78%. The element classifier occasionally misroutes complex hybrid diagrams (part schematic, part photograph) to the cheaper GPT-4o-mini path, causing 6% of those images to have incomplete descriptions; a manual review of misclassified images during the first indexing run identified the issue and led to adding a "hybrid diagram" classification category.
