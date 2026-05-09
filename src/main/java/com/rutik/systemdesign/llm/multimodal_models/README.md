# Multimodal Models

## 1. Concept Overview

Multimodal models process and generate multiple types of data — text, images, audio, video, and code — within a single unified model. The most commercially important modality combination is vision + language (Vision Language Models, or VLMs), which enables applications like medical image analysis, document understanding, visual Q&A, and chart interpretation.

The trend from 2023-2025: all frontier LLMs have become multimodal. GPT-4o, Claude 3.5, Gemini 1.5 Pro, and LLaMA 3.2 all support image input natively. The question is no longer "can LLMs see?" but "how well do they reason about visual information?"

---

## Intuition

> **One-line analogy**: Multimodal models are like a person who can look at an image and discuss it — they bridge the gap between the visual world and language by learning to represent both in the same "mental" space.

**Mental model**: A Vision Language Model (VLM) has two main components: a vision encoder (like CLIP or SigLIP) that converts images into token-like vectors, and a language model that processes those vectors alongside text tokens. The key trick: train the system so that an image of a cat produces vectors that live near the word "cat" in the embedding space. Once visual and text representations are aligned, the language model can reason about them together.

**Why it matters**: Multimodality unlocks applications impossible with text-only models — medical imaging (read X-rays), document understanding (read invoices with tables), code from screenshots, visual Q&A, chart interpretation. All frontier models are now multimodal because vision dramatically expands the input modalities a model can handle.

**Key insight**: The key challenge is "modality alignment" — images and text live in very different spaces. CLIP-style contrastive training (match image embeddings to text embeddings for the same concept) was the breakthrough that made practical VLMs possible.

---

## 2. Core Principles

- **Shared representation space**: Multimodal models work by projecting different modalities into the same embedding space, where a text token and an image patch can "attend to each other" via the transformer's attention mechanism.
- **Vision encoder + LLM**: The dominant architecture pairs a pre-trained vision encoder (CLIP, SigLIP) with a pre-trained LLM, connected by a learnable projection layer.
- **Instruction tuning for vision**: Like text-only models, multimodal models need instruction fine-tuning to follow image-related instructions.
- **Trade-offs between modality depth**: Unified models understand all modalities but may be shallower than specialized models.

---

## 3. Types / Architectures

### 3.1 Vision Language Models (VLMs)

**Architecture (LLaVA / LLaMA-Vision style):**
```
Image
  |
  v
[Vision Encoder] (CLIP ViT-L/14 or SigLIP)
  Divide image into patches (e.g., 14×14 pixels each)
  Encode each patch → embedding
  Output: N visual tokens [v1, v2, ..., vN]
  |
  v
[Projection Layer] (Linear or MLP)
  Map from vision embedding dim → LLM embedding dim
  v1 → word-like token for LLM
  |
  v
[LLM] (LLaMA, Mistral, Qwen, etc.)
  Concatenate visual tokens + text tokens
  [v1, v2, ..., vN, t1, t2, t3, ...]
  Standard autoregressive generation
```

**Training stages:**
```
Stage 1: Alignment pretraining
  Freeze LLM and vision encoder
  Train only the projection layer
  Data: image-caption pairs (595K LAION-CC-SBU)
  Goal: align visual and text representations

Stage 2: Instruction fine-tuning
  Unfreeze LLM (or use LoRA)
  Data: visual instruction following (158K LLaVA-Instruct)
  Goal: follow visual instructions ("Describe the image", "What is wrong?")
```

**Key VLMs:**
| Model | Vision Encoder | LLM | Context | Strengths |
|-------|---------------|-----|---------|-----------|
| LLaVA-1.6 | CLIP ViT-L | LLaMA 7B | 4K | Open; baseline |
| LLaMA 3.2 Vision | Custom | LLaMA 3.2 | 128K | Open; 11B/90B |
| InternVL2 | InternViT | InternLM2 | 8K | Best open-source quality |
| GPT-4o | Unknown | GPT-4o | 128K | Best overall; closed |
| Gemini 1.5 Pro | Proprietary | Gemini | 1M | Long context; multimodal |
| Claude 3.5 Sonnet | Unknown | Claude 3.5 | 200K | Best OCR; document understanding |
| Qwen-VL | ViT | Qwen | 32K | Multilingual; open |

### 3.2 Diffusion Models (Text-to-Image)

Not LLMs but closely related in the AI landscape:

```
Latent Diffusion:
  1. Encode image → latent space (VAE encoder)
  2. Add Gaussian noise iteratively → pure noise
  3. Train model to denoise → predict noise at each step
  4. At inference: start from noise, denoise conditioned on text
  5. Decode latent → image (VAE decoder)

Text conditioning:
  Text prompt → CLIP/T5 text encoder → text embeddings
  Cross-attention: each denoising step attends to text embeddings

Key models:
  Stable Diffusion (Stability AI): open weights; community ecosystem
  DALL-E 3 (OpenAI): integrated with ChatGPT; high quality
  Midjourney: subscription; best artistic quality
  Flux (Black Forest Labs): best open-source quality (2024)
  Imagen (Google): large T5 text encoder; photorealistic
```

### 3.3 Speech Models

**Speech-to-Text (ASR):**
```
Whisper (OpenAI):
  Input: raw audio → mel spectrogram → transformer encoder
  Output: transcribed text (multilingual, with timestamps)
  Architecture: encoder-decoder transformer
  Models: tiny(39M) → small → base → large-v3(1.5B)
  Quality: near human-level on English; excellent multilingual

Wav2Vec 2.0 (Meta):
  Self-supervised pre-training on unlabeled audio
  Fine-tuned for ASR with CTC loss
  Excellent low-resource language performance
```

**Text-to-Speech (TTS):**
```
Bark (Suno AI): realistic speech with emotion, laughter, music
ElevenLabs: voice cloning with very little data
OpenAI TTS: gpt-4-voice; natural, consistent voices
XTTS (Coqui): open source; voice cloning
```

**Native multimodal audio:**
```
GPT-4o (realtime API):
  Audio input → audio output directly (no text intermediate)
  Captures tone, emotion, non-verbal cues
  Very low latency (sub-second response)
  Enables true voice assistants

Gemini 1.5 Pro:
  Audio + video + text in single context
  Can analyze hour-long audio recordings
```

### 3.4 Video Models

```
Video understanding:
  Video-LLaMA: video frames sampled → encoded → LLM
  InternVideo2: 1B video clips pre-training
  Gemini 1.5 Pro: can process 1-hour video in context

Video generation:
  Sora (OpenAI): world model; 1-min realistic video
  Runway Gen-3: commercial video generation
  CogVideoX: open-source video generation
  Kling, HailuoAI: commercial Asian competitors

Architecture: DiT (Diffusion Transformer) replacing UNet for video
  Apply attention across spatial + temporal dimensions
```

---

## 4. Architecture Diagrams

### VLM Complete Flow
```
Image + Text Question
     |           |
     v           v
[Vision      [Text
 Encoder]    Tokenizer]
 ViT-L/14   BPE tokens
     |           |
     v           v
[Projection] [Embedding]
 Linear MLP  Lookup table
     |           |
     +-----+-----+
           |
           v
     [Interleaved Tokens]
     [img1][img2]...[imgN][question_tokens...]
           |
           v
     [LLM Decoder]
     Attention across all tokens
     (visual tokens + text tokens)
           |
           v
     Text Response
```

### CLIP Pre-training (Foundation for VLMs)
```
400M image-text pairs from internet:
  Image: "A photo of a dog playing fetch"
  Text: "A photo of a dog playing fetch"

  [Image Encoder] → image_embedding
  [Text Encoder]  → text_embedding

  Contrastive loss:
    Maximize cosine_sim(image_emb, paired_text_emb)
    Minimize cosine_sim(image_emb, other_text_emb)

Result: shared embedding space where
  matching image and text have high similarity
```

---

## 5. How It Works — Detailed Mechanics

### Vision Encoding Details

```
Image preprocessing:
  Resize to 224×224 or 336×336 pixels
  Normalize: (pixel - mean) / std
  Divide into patches: 14×14 or 16×16 pixels per patch
  Image 224×224 with 14×14 patches = (224/14)² = 256 patch tokens

High-resolution handling:
  Problem: 1024×1024 image → 5329 patches → very long sequence
  Solution 1: Resize down (loses detail)
  Solution 2: AnyRes (LLaVA-NeXT): divide into sub-images
    Encode each sub-image separately → concatenate tokens
  Solution 3: Dynamic resolution (InternVL): variable tile count

OCR capability:
  High resolution is critical for text in images
  Claude 3.5 Sonnet excels at document OCR
  GPT-4o excels at mathematical expressions in images
```

### Multimodal Training Data

```
Pre-alignment data (image-caption pairs):
  LAION-5B: 5 billion image-text pairs from internet (noisy but large)
  CC12M: 12M conceptual captions (higher quality, smaller)
  COYO: 700M curated pairs

Instruction fine-tuning data:
  LLaVA-Instruct: GPT-4 generated (question, image, answer) tuples
  ShareGPT4V: GPT-4V generated descriptions + QA
  DocVQA: document understanding QA
  ChartQA: chart/graph understanding
  ScienceQA: science diagrams

Medical multimodal:
  PathVQA: pathology image QA
  VQA-RAD: radiology QA
  SLAKE: medical visual QA
```

---

## 6. Real-World Examples

### GPT-4o Vision
- Processes images up to 20MB
- "High detail" mode: 4x more tokens, better for dense text/diagrams
- Used for: document processing, accessibility (describe images for visually impaired), medical image analysis, code screenshot debugging
- Demonstrated: solve math problems from photo of whiteboard

### Claude 3.5 Sonnet (Vision)
- Best-in-class OCR for documents, forms, tables
- Accurately extracts text from complex PDFs with mixed layouts
- Used for: legal document review, financial statement analysis, form processing
- Can describe complex charts and technical diagrams

### Gemini 1.5 Pro
- 1M token context = process entire movies
- Needle-in-a-haystack: find a specific frame in a 1-hour video
- Used for: long video analysis, multi-document processing with images

### Medical Imaging: Med-PaLM M
- Google's multimodal medical model
- Radiology: chest X-ray analysis, skin condition classification
- Performance: surpasses radiologists on some classification tasks
- Not deployed clinically — regulatory and liability issues remain

---

## 7. Tradeoffs

| Model | Image Quality | Video | Audio | Context | Open? |
|-------|-------------|-------|-------|---------|-------|
| GPT-4o | Excellent | No | Yes | 128K | No |
| Claude 3.5 | Excellent (OCR) | No | No | 200K | No |
| Gemini 1.5 Pro | Very good | Yes | Yes | 1M | No |
| LLaMA 3.2 Vision | Good | No | No | 128K | Yes |
| InternVL2 | Very good | No | No | 8K | Yes |

---

## 8. When to Use / When NOT to Use

### Use VLMs When:
- Input contains images, charts, diagrams, or screenshots
- Document processing with mixed text and images
- Visual Q&A, visual reasoning
- OCR for complex document layouts

### Use Specialized Models When:
- Pure computer vision (detection, segmentation) → YOLO, SAM
- Pure OCR on clean documents → Tesseract, AWS Textract
- Face recognition → specialized face models
- Video analysis at scale → VideoLLaMA, specialized video models

---

## 9. Common Pitfalls

1. **Image resolution too low**: Shrinking high-res images to 336×336 loses text and fine details. Use high-detail mode.
2. **Overestimating OCR capability**: VLMs are better at understanding than exact transcription. For legal/financial, use specialized OCR.
3. **Not testing on domain images**: General VLMs may struggle with medical, industrial, or satellite imagery.
4. **Ignoring image token cost**: 1 high-res image = 1000-4000 tokens. Cost and latency add up quickly.
5. **Assuming spatial reasoning is reliable**: VLMs struggle with precise spatial/geometric reasoning. Verify on your specific task.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **GPT-4o API** | Vision + text | Best quality; image input |
| **Claude 3.5 API** | Vision + text | Best OCR; document understanding |
| **LLaMA 3.2 Vision** | Open VLM | 11B/90B; self-hostable |
| **InternVL2** | Open VLM | Best open-source quality |
| **Whisper** | ASR | OpenAI; multilingual; state of art |
| **ElevenLabs** | TTS + voice clone | Commercial; highest quality |
| **Stable Diffusion** | Text-to-image | Open; massive ecosystem |
| **DALL-E 3** | Text-to-image | Best prompt following |
| **Flux** | Text-to-image | Best open-source (2024) |
| **LLaVA** | Open VLM | Research; widely used baseline |

---

## 11. Interview Questions with Answers

**Q: How do Vision Language Models work architecturally?**
A: Most VLMs use a three-component architecture: (1) a vision encoder (usually CLIP or SigLIP ViT) that divides an image into patches and encodes each patch as an embedding; (2) a projection layer (MLP) that maps vision embeddings into the same dimension as the LLM's text embeddings; (3) the LLM itself, which processes the interleaved sequence of visual and text tokens using standard self-attention. Training happens in two stages: alignment pre-training (trains only the projection layer on image-caption pairs) and instruction fine-tuning (trains the LLM + projection layer on visual instruction data).

**Q: What is CLIP and why is it important for multimodal AI?**
A: CLIP (Contrastive Language-Image Pre-training) is a model trained on 400M internet image-text pairs using contrastive learning — matching image embeddings to their text descriptions. It creates a shared embedding space where semantically similar images and text are close together. CLIP is important because: (1) it's the standard vision encoder used by most VLMs (LLaVA, LLaMA Vision); (2) it enables zero-shot image classification (compare image embedding to text descriptions of categories); (3) CLIP embeddings enable multimodal search.

**Q: What are the main challenges in evaluating vision language models?**
A: (1) Hallucination — models describe objects/text not present in images; (2) Spatial reasoning — "which object is to the left of X" is harder than simple identification; (3) OCR accuracy — precise text extraction requires specialized evaluation (exact character accuracy); (4) Domain gap — models trained on natural images may underperform on medical/satellite/industrial images; (5) Benchmark contamination — popular benchmarks (VQAv2, MMBench) appear in training data. Use domain-specific evaluation sets.

---

## 12. Best Practices

1. **Use high-detail mode for dense images** — charts, documents, screenshots need maximum resolution.
2. **Preprocess images** — crop to relevant region, adjust contrast for poor-quality images.
3. **Test on representative domain images** — VLM quality varies dramatically by image type.
4. **For OCR-critical applications** — validate against specialized OCR tools (AWS Textract, Google Document AI).
5. **Track image token costs** — high-resolution images cost 4-10× more than text.
6. **Combine with structured extraction** — use VLM to understand layout, then extract fields programmatically.

---

## 13. Case Study: Automated Insurance Claim Processing with VLM

**Problem:** Insurance company receives 5,000 photo claims/day (car accidents, property damage). Human adjusters spend 20 minutes per claim reviewing photos, estimating damage.

**Solution:**
```
Input: 3-10 photos per claim

Step 1: Scene Understanding (GPT-4o, high-detail)
  For each photo:
    - Identify: vehicle make/model/year (cross-reference with claim)
    - Damage assessment: location, severity (minor/moderate/severe/total loss)
    - Affected parts list with confidence scores

Step 2: Structured Extraction
  JSON schema:
  {
    "vehicle": {"make": "Toyota", "model": "Camry", "year": 2021},
    "damage_locations": ["front bumper", "hood", "left headlight"],
    "severity": "moderate",
    "estimated_repair_cost_range": "$3,000-$5,000",
    "requires_human_review": false
  }

Step 3: Validation Rules
  If severity == "total loss": always require human review
  If vehicle identification confidence < 0.8: require human review
  If repair estimate > $10,000: require human review

Step 4: Queue for human adjuster review (flagged cases only)
```

**Results:**
- 67% of claims auto-processed without human review
- Human adjuster time reduced from 20 min to 8 min (verification only)
- Accuracy: VLM vehicle identification 94%; damage assessment within 15% of human estimate
- Processing time: 45 seconds per claim (vs. 20 minutes)
- Annual savings: $3.2M in adjuster time
