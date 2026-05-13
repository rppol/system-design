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

---

## 11. Best Practices

1. **Extract at high resolution** — minimum 150 DPI, 300 DPI for documents with dense tables or small text; poor resolution produces poor descriptions and embeddings.
2. **Include surrounding context in vision LLM prompts** — ±200 tokens around each image dramatically improves description accuracy.
3. **Build separate text and image retrieval paths** — dual retrieval (text search + CLIP/description search) then merge; single unified retrieval misses cross-modal signals.
4. **Store original images in blob storage** — always preserve the original image even if you have a text description; the generation LLM benefits from seeing the actual image.
5. **Validate extraction quality** — automated checks: does the description contain numeric values consistent with nearby text? Does the table have the expected number of rows/columns?
6. **Limit images per generation call** — cap at 2-3 images per query; prefer text descriptions for routine queries, original images only for queries that explicitly need visual detail.
7. **Benchmark vision LLM options** — accuracy and cost vary significantly; evaluate GPT-4o, Claude 3.5, and Gemini 1.5 Pro on your specific document types before committing.
