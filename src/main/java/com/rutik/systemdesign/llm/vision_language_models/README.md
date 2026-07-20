# Vision-Language Models (VLMs)

> Cross-reference: [`../multimodal_models/`](../multimodal_models/) covers the broader multimodal landscape including audio and video modalities. This module focuses specifically on the **vision-language intersection**: CLIP contrastive training, dual encoder architecture, LLaVA-style adapters, visual grounding, and VQA benchmarks.

---

## 1. Concept Overview

Vision-Language Models (VLMs) jointly understand images and text. Unlike pure computer vision models that classify or detect objects, VLMs can describe images, answer questions about them, retrieve images using natural language queries, and reason about visual content as fluidly as an LLM reasons about text.

**Three primary architectural families:**

1. **Dual Encoder (CLIP-style)**: separate vision encoder + text encoder trained with contrastive loss to align embeddings in a shared space. Fast, scalable, but produces coarse global representations.

2. **Encoder-Decoder (captioning/VQA)**: vision encoder feeding an autoregressive text decoder, often via a learned bridge (Q-Former in BLIP-2). Produces free-form text about images.

3. **LLM + Vision Adapter (LLaVA-style)**: a powerful pre-trained LLM augmented with a lightweight vision projection module. Leverages the LLM's reasoning and instruction-following abilities for complex visual tasks.

**Key capability distinctions:**

| Capability | Description | Representative Models |
|-----------|-------------|----------------------|
| Image-text matching | "Does this image match this caption?" — fast, coarse | CLIP, SigLIP, ALIGN |
| Image captioning | "Describe this image" — free-form text generation | BLIP-2, GIT, CogVLM |
| Visual Question Answering (VQA) | "How many people are in this image?" — open-ended answers | LLaVA, InstructBLIP, GPT-4V |
| Visual Grounding | "Where is the red car?" — bounding box output | Grounding DINO, Florence-2 |
| OCR + document understanding | Reading text embedded in images | PaddleOCR, Florence-2, Claude vision |
| Multi-image reasoning | Comparing or reasoning across multiple images | GPT-4o, Gemini 1.5 Pro, LLaVA-1.6 |

**Key benchmarks**: VQAv2 (general VQA), MMMU (college-level multi-discipline), SEED-Bench (comprehensive visual understanding), MMBench (multilingual VQA), TextVQA (OCR-heavy VQA).

---

## 2. Intuition

**One-line analogy**: A VLM is a model that speaks both image and text fluently — it can read a photo and describe it, compare images to captions, ground bounding boxes from a text description, and reason about visual content the same way an LLM reasons about text.

**Mental model**: Images are tokenized into patches (ViT: 196 patches for a 224x224 image at 16x16 patch size). Each patch becomes a fixed-length vector, just like a text token becomes an embedding. A VLM's core challenge is **aligning** the visual patch embedding space with the text token embedding space so that the embedding for "a dog playing fetch" and the embedding for a corresponding image end up near each other in the shared space.

**Why it matters**: Before VLMs, connecting vision and language required bespoke pipelines — a detector to find objects, a captioner to describe them, a text model to reason about the captions. VLMs collapse this into a single model that handles vision and language natively, enabling emergent capabilities that no individual training signal explicitly taught.

**Key insight**: CLIP (2021) proved that contrastive learning at scale — 400M image-text pairs scraped from the internet, no explicit object labels — produces remarkably general visual representations. GPT-4V demonstrated that plugging a vision encoder into a powerful LLM produces emergent visual reasoning (chart reading, code screenshot debugging, medical imaging Q&A) that no individual fine-tuning step explicitly encoded.

**The patch count arithmetic**:
```
224 / 16 = 14 patches per dimension
14 x 14 = 196 patches total per image
Each patch = 16 x 16 x 3 RGB values = 768 raw values
-> linear projection -> d_model embedding (typically 1024 for ViT-L)
```

**Read it like this.** "Chop the image into a grid of fixed squares, flatten each square into a list of numbers, and hand the LLM that grid as if it were a sentence."

Everything downstream — cost, latency, context budget, how much detail survives — is decided by this one grid size. Halve the patch size and you quadruple the token count.

| Symbol | What it is |
|--------|------------|
| `224` | Image side length in pixels after resizing. ViT requires a fixed input size |
| `16` | Patch side length in pixels. The grid cell the image is diced into |
| `224 / 16 = 14` | Patches along one side. Must divide evenly — this is why input sizes are fixed |
| `14 x 14 = 196` | Total patches = the sequence length the transformer sees |
| `16 x 16 x 3` | Raw values in one patch: width x height x 3 colour channels (RGB) = 768 |
| `d_model` | Width of each patch embedding after the linear projection (1024 for ViT-L) |
| `[CLS]` | One extra learned token prepended to hold the whole-image summary |

**Walk one example.** Push a single 224x224 photo all the way to a token count:

```
  image 224 x 224, patch 16 x 16

  patches per side  = 224 / 16          =  14
  patches per image = 14 x 14           = 196
  values per patch  = 16 x 16 x 3 (RGB) = 768
  ViT sequence      = 196 + 1 [CLS]     = 197 tokens

  CLIP keeps  1 token   ([CLS] only)      -> a single global vector
  LLaVA keeps 196 tokens (drops [CLS])    -> spatial detail survives

  LLaVA-1.6 tiling: 4 tiles + 1 thumbnail, each 196 tokens
  total = 5 x 196 = 980 visual tokens for ONE image
```

**Why the `+1` for `[CLS]` matters.** The 196 patch tokens each describe a 16x16 corner of the picture; none of them describes the picture. `[CLS]` is a slot with no pixels attached, so attention is free to fill it with whatever global summary the training objective rewards — which is exactly the vector CLIP pools and compares against text. Drop `[CLS]` and CLIP has nothing to embed; keep only `[CLS]` and you get CLIP's blindness to location.

---

## 3. Core Principles

**Contrastive alignment**: Push matching image-text pairs together in embedding space, push non-matching pairs apart. The InfoNCE loss is the standard objective: maximizing the log probability that the correct pairing is found among N candidates in a batch. With N=32,768 (CLIP's batch size), each positive pair is contrasted against 32,767 negatives — extremely hard negatives that force the model to learn fine-grained distinctions.

**Patch-based image tokenization**: Divide an image into non-overlapping NxN patches. Embed each patch with a linear projection (or CNN, or ViT block). The resulting sequence of patch embeddings is analogous to a sequence of token embeddings in an LLM. Position embeddings are added to retain spatial structure.

**Cross-modal attention**: In encoder-decoder and LLaVA-style models, text tokens can attend to visual patch tokens (and vice versa) through standard transformer attention. This is how a model learns that the word "dog" in the question should attend to the patch embeddings representing the dog region in the image.

**Frozen backbone fine-tuning (LLaVA principle)**: Freeze the LLM backbone, train only the vision-to-language projection adapter. This is computationally efficient and prevents catastrophic forgetting of the LLM's text capabilities. The ViT encoder is also typically frozen — it was trained on large-scale vision data and provides robust patch representations. Only the small MLP bridge needs learning.

**Instruction tuning for vision**: Adapt the base VLM to follow natural language instructions about images. "Describe the chart", "find all red objects", "what error does this code screenshot show?" — these require a different distribution than the original pre-training and must be explicitly trained with visual instruction-following datasets.

**Visual grounding as a distinct capability**: Generating bounding box coordinates requires a different output head or special tokens (e.g., `<box>x1, y1, x2, y2</box>`). Standard VQA models that output free text cannot ground unless explicitly trained to do so.

---

## 4. Types / Architectures / Strategies

### 4.1 Dual Encoder (CLIP-style)

Architecture: two independent encoders — a ViT (or ResNet) for images and a Transformer for text — trained jointly with contrastive loss. The encoders share no weights. At inference, embeddings from both modalities land in the same high-dimensional space (typically 512-d or 768-d) and can be compared with cosine similarity.

**Training objective**:
- Batch of N image-text pairs
- Compute N x N cosine similarity matrix
- Cross-entropy loss on rows (image-to-text direction) + columns (text-to-image direction) simultaneously
- Temperature parameter tau (learnable) scales similarities before softmax

**Key models**:
- CLIP (OpenAI, 2021): ViT-L/14 image encoder + Transformer text encoder, 400M training pairs
- ALIGN (Google, 2021): similar approach, 1.8B noisy pairs, EfficientNet image encoder
- SigLIP (Google, 2023): sigmoid loss instead of softmax; more stable training, better at smaller batch sizes
- EVA-CLIP (BAAI): scaled to ViT-18B; state-of-the-art image encoder for downstream VLMs

**Use cases**: zero-shot image classification, semantic image search, image-text retrieval, fast visual filtering.

**Limitation**: produces a single global embedding per image. No spatial awareness, cannot count objects, cannot localize, cannot generate text. The embedding for "a dog in the top-left corner" is nearly identical to "a dog in the center." CLIP does not know where in the image anything is.

---

### 4.2 Encoder-Decoder (Captioning and VQA)

Architecture: a vision encoder (typically a frozen ViT) feeding into an autoregressive language model decoder. The connection between vision and language is the critical design question.

**Q-Former (BLIP-2)**: A lightweight learned module with a fixed number of query tokens (32 by default). The query tokens attend to image patch embeddings via cross-attention and extract a compressed, fixed-length visual feature vector. These 32 query outputs then feed into a frozen LLM (OPT-2.7B or FlanT5). The Q-Former is the only trained component; both ViT and LLM are frozen.

Advantage: the frozen LLM preserves text capabilities; the Q-Former is tiny and efficient. Disadvantage: compressing 196 image patches into 32 query tokens loses spatial detail.

**Key models**:
- BLIP-2 (Salesforce, 2023): Q-Former + frozen OPT/FlanT5
- InstructBLIP (Salesforce): BLIP-2 variant with instruction tuning
- GIT (Microsoft): simpler architecture, single vision-augmented GPT decoder
- CogVLM (Tsinghua): visual expert layers interleaved with LLM transformer layers

---

### 4.3 LLM + Vision Adapter (LLaVA-style)

Architecture: CLIP ViT image encoder feeding a small MLP projection layer whose output is prepended to the LLM's input sequence as visual tokens.

**Two-stage training**:
- Stage 1 (Feature Alignment): freeze ViT + LLM; train only the 2-layer MLP projection on 595K image-caption pairs (LLaVA-CC3M-Pretrain-595K). Goal: teach the MLP to map ViT embeddings into a space the LLM can interpret. Duration: ~6 hours on 8xA100.
- Stage 2 (Instruction Tuning): unfreeze LLM (ViT remains frozen in most variants); train MLP + LLM on 665K visual instruction-following examples. Data includes LLaVA-Instruct-150K, COCO conversations, ScienceQA. Goal: follow user instructions about images.

**Key models**:
- LLaVA-1.5 (2023): CLIP ViT-L/14@336 + 2-layer MLP + Vicuna-13B or Llama-2-13B; 336px input
- LLaVA-1.6 / LLaVA-NeXT (2024): dynamic high-resolution tiling (up to 1344px), better chart and document understanding
- Qwen-VL (Alibaba): strong multilingual visual understanding
- InternVL2 (OpenGVLab): InternViT-6B encoder + InternLM; competitive with GPT-4V on MMMU
- MiniCPM-V: optimized for edge deployment; strong performance at 3B parameters
- LLaVA-Med: domain-adapted version fine-tuned on medical images

**Advantage over BLIP-2**: all 196 (or more) visual patch tokens are passed directly to the LLM rather than compressed through a Q-Former bottleneck. The LLM can attend to individual patch embeddings, preserving spatial detail.

---

### 4.4 Unified Multimodal LLMs (GPT-4V Class)

Architecture: a single model that handles text and image input natively, with vision tokens interleaved directly with text tokens in the input sequence. No separate encoder that feeds a separate decoder — one unified transformer stack.

**Key models**:
- GPT-4V / GPT-4o (OpenAI): most capable; GPT-4o natively processes images, text, and audio in one model
- Gemini 1.5 Pro (Google): multi-image and video frame support; native multimodality from pre-training
- Claude 3 / Claude 3.5 (Anthropic): strong on text-heavy images, document extraction, form parsing
- Flamingo (DeepMind, 2022): pioneered the interleaved image-text sequence architecture

**Capabilities not available in simpler VLMs**:
- Multi-image reasoning: "what changed between these two photos?"
- Video frame understanding: process N frames as N interleaved images
- Interleaved image-text sequences: a document with text and images mixed inline

**Cost**: GPT-4o charges ~$2.50/1M input tokens for text. Image tokens add separately: a 512px image costs ~$0.002 at current pricing. High-volume workloads can reach $10K/month quickly.

---

### 4.5 Visual Grounding

Task: given a text query, predict bounding boxes in the image. "Find all cats in this image" -> `[(x1,y1,x2,y2), ...]`. Output is spatial coordinates, not free text.

Architecture differs from standard VQA: the model needs a detection head (or special output tokens representing coordinates) in addition to the text decoder.

**Key models**:
- Grounding DINO (IDEA-Research): DINO object detector + text encoder; text-conditioned open-vocabulary detection; ~100ms inference
- Florence-2 (Microsoft): unified vision model outputting text, bounding boxes, or segmentation masks depending on the prompt task token; single model for captioning, grounding, and OCR
- SAM (Meta) + CLIP: segment anything + clip re-ranking for text-driven segmentation; widely used in robotics for "pick the red mug" grounding
- Shikra / Qwen-VL: VLMs that output bounding box coordinates as text tokens for combined VQA + grounding

---

## 5. Architecture Diagrams

### CLIP Contrastive Loss — the N x N Similarity Matrix

The flow below ends at `sim_matrix[N,N]`, but the loss is easiest to see as the matrix
itself. For a batch of N image-text pairs, the N diagonal cells are the true matches and
every off-diagonal cell is a negative — so one batch yields N positives and N^2 - N
negatives for free (cell values illustrative):

```
   S[i,j] = (img_i . txt_j) / tau          softmax over each ROW
                                            (image I_i -> find its caption)
                T1      T2      T3      T4
        +--------------------------------+
   I1   | (.90)   .10     .20     .00    |   ( ) = diagonal = the N MATCHED
   I2   |  .10   (.80)    .00     .30    |         pairs  -> push UP
   I3   |  .20    .10    (.90)    .10    |
   I4   |  .00    .20     .10    (.70)   |   bare  = off-diagonal = N^2 - N
        +--------------------------------+         MISMATCHES -> push DOWN
          softmax over each COLUMN
          (caption T_j -> find its image)

   Loss = CE(rows, diagonal) + CE(columns, diagonal), averaged. Bigger batch =
   bigger grid = harder negatives, which is why CLIP trains at N = 32,768
   (each positive is contrasted against 32,767 negatives in the same step).
```

```
CLIP Dual Encoder (Training)
============================================================

  Image Batch           Text Batch
  [I1, I2, ..., IN]     [T1, T2, ..., TN]
       |                      |
  ViT-L/14              Transformer
  (patch embed)         (token embed)
       |                      |
  [CLS] pooling         [EOS] pooling
       |                      |
  Linear proj           Linear proj
       |                      |
  L2 normalize          L2 normalize
       |                      |
  img_embeds[N,512]     txt_embeds[N,512]
       \                     /
        \                   /
         sim_matrix[N, N] = img_embeds @ txt_embeds.T / tau
         (diagonal = positive pairs, off-diagonal = negatives)
         Loss: CE on rows + CE on columns simultaneously

============================================================
CLIP Inference (Image Retrieval)
============================================================

  Query text: "red sneakers with white sole"
       |
  Text Encoder -> text_emb [512-d]
       |
  Cosine similarity against 50M cached image embeddings
       |
  Top-K results (ANN search via FAISS or similar)

============================================================
LLaVA Architecture (Inference)
============================================================

  Input Image (336x336 for LLaVA-1.5)
       |
  CLIP ViT-L/14 (FROZEN)
       |
  196 visual patch tokens (each 1024-d)
       |
  2-Layer MLP Projection (TRAINED, Stage 1 only)
       |
  196 projected visual tokens (LLM hidden size, e.g. 4096-d)
       |
  [SYSTEM prompt tokens]
  [196 visual tokens]          <- prepended before user text
  [USER: "what is in this image?"]
  [ASSISTANT:]
       |
  Llama-3-8B or Vicuna-13B (FROZEN Stage 1, UNFROZEN Stage 2)
       |
  Autoregressive token generation

============================================================
LLaVA-1.6 High-Resolution Tiling
============================================================

  High-res image (e.g. 1344x336)
       |
  Divide into dynamic tiles
  +------+------+------+------+
  | tile1| tile2| tile3| tile4|  (each 336x336)
  +------+------+------+------+
  + 1 thumbnail (full image downsampled to 336x336)
       |
  5x ViT inference -> 5 x 196 = 980 visual tokens + 196 thumbnail
       |
  MLP projection -> concatenated -> LLM

  Result: ~4x more visual detail than LLaVA-1.5

============================================================
ViT Patch Embedding Detail
============================================================

  224x224 image, patch_size=16
  +--+--+--+--+--+-  14 patches per row
  |p |p |p |p |p |..
  +--+--+--+--+--+-  14 patches per col
  ...
  Total: 14 x 14 = 196 patches

  Each patch (16x16x3) -> flatten -> [768 values]
                       -> linear projection -> [d_model embedding]
  + learnable position embedding per patch
  + prepend [CLS] token

  Final ViT output: [CLS, p1, p2, ..., p196] = 197 tokens
  CLIP uses [CLS] only for global embedding
  LLaVA uses [p1..p196] (all patch tokens) for spatial detail

============================================================
VQA Inference Pipeline
============================================================

  User query: "How many red apples are on the table?"
       |
  Image tokenization (ViT)
       |               \
  Visual tokens     Text tokens ("How many red apples...")
       |               /
  Cross-attention (LLM processes interleaved sequence)
       |
  Autoregressive decoding: "There are 4 red apples on the table."
```

---

## 6. How It Works — Detailed Mechanics

### CLIP Contrastive Training Math

Given a batch of N image-text pairs `(I_1, T_1), ..., (I_N, T_N)`:

1. Compute image embeddings: `f_i = ViT(I_i)`, normalize: `f_i = f_i / ||f_i||`
2. Compute text embeddings: `g_j = TextEncoder(T_j)`, normalize: `g_j = g_j / ||g_j||`
3. Compute similarity matrix: `S[i,j] = dot(f_i, g_j) / tau` (tau = learnable temperature, initialized to 0.07)
4. Row-wise cross-entropy (image -> text): treat row i as classification over N text candidates; label = i
5. Column-wise cross-entropy (text -> image): treat column j as classification over N image candidates; label = j
6. Total loss = (row_CE + col_CE) / 2

At CLIP's batch size of N=32,768: each positive pair is contrasted against 32,767 negatives. This is what makes contrastive learning at scale so powerful — the negatives are genuinely hard.

**SigLIP improvement**: Replace softmax cross-entropy with sigmoid binary cross-entropy. Each (i,j) pair is independently classified as matching (1) or not matching (0). This removes the global softmax normalization, making training more stable at large scales and enabling effective training even with smaller batch sizes.

```python
# CLIP InfoNCE loss (simplified)
import torch
import torch.nn.functional as F

def clip_loss(image_embeddings: torch.Tensor, text_embeddings: torch.Tensor,
              temperature: float = 0.07) -> torch.Tensor:
    """
    image_embeddings: [N, d] normalized
    text_embeddings:  [N, d] normalized
    """
    logits = (image_embeddings @ text_embeddings.T) / temperature  # [N, N]
    labels = torch.arange(len(image_embeddings), device=logits.device)
    loss_i2t = F.cross_entropy(logits, labels)      # image -> text direction
    loss_t2i = F.cross_entropy(logits.T, labels)    # text -> image direction
    return (loss_i2t + loss_t2i) / 2
```

**What the formula is telling you.** "For every image in the batch, make picking its own caption out of a lineup of N captions a multiple-choice question — and grade the model on how confidently it picks the right one."

InfoNCE is not a similarity target; it is a *ranking* target dressed as classification. The model is never told "this pair should score 0.9" — only "this pair must outscore the other N-1 in the same batch." That is why the batch itself is the training signal.

| Symbol | What it is |
|--------|------------|
| `f_i`, `g_j` | L2-normalized image and text embeddings. Normalized, so `dot(f, g)` is cosine similarity in `[-1, 1]` |
| `S[i,j]` | Similarity of image `i` to caption `j`, divided by tau. The `[N, N]` grid of logits |
| Diagonal `S[i,i]` | The true pairs — the N correct answers the loss pushes up |
| Off-diagonal | The `N^2 - N` in-batch negatives, free of charge, pushed down |
| `tau` | Learnable temperature, initialized to `0.07`. Divides every similarity before softmax |
| `labels = arange(N)` | "The correct answer for row `i` is column `i`" — the diagonal, written as class labels |
| `CE(logits, labels)` | Cross-entropy: `-log(probability assigned to the correct column)` |
| `(loss_i2t + loss_t2i) / 2` | Grade both directions — find the caption AND find the image — then average |

**Walk one example.** One row of the `N = 4` matrix from Section 5 (image `I1` against captions `T1..T4`), correct answer `T1`:

```
  cosine sims from the matrix :   .90     .10     .20     .00
  correct column is T1 (the diagonal cell)

  with tau = 0.07 (CLIP's init)
    logits = sim / tau        12.857   1.429   2.857   0.000
    softmax                    0.9999  0.0000  0.0000  0.0000
    loss = -log(0.9999)      = 0.0001      <- solved; almost no gradient left

  with tau = 1.0 (no sharpening)
    logits = sim              0.900   0.100   0.200   0.000
    softmax                   0.4251  0.1910  0.2111  0.1728
    loss = -log(0.4251)     = 0.8555      <- still plenty of signal
```

**Why the temperature exists at all.** Cosine similarity is trapped in `[-1, 1]`, so the widest gap the model can ever express between a true pair and a negative is 2.0 — far too flat for softmax to produce a confident distribution. Dividing by `tau = 0.07` multiplies every gap by ~14x, stretching that cramped range into logits softmax can actually separate. Making tau *learnable* lets the model choose its own confidence: it sharpens tau as alignment improves, and CLIP clamps it to stop the loss collapsing to zero by driving tau toward 0 instead of learning anything.

**Why bigger batches are the whole game.** A model that guesses randomly scores `-log(1/N) = ln(N)`, so the batch size sets both the difficulty and the loss floor:

```
  random-guess loss = ln(N)
    N = 4       ->  ln 4      =  1.386   (1 in 4 -- trivial)
    N = 32,768  ->  ln 32768  = 10.397   (1 in 32,768 -- brutal)

  one CLIP batch at N = 32,768 yields
    positives = 32,768
    negatives = 32,768^2 - 32,768 = 1,073,709,056 off-diagonal cells
```

Over a billion negative comparisons in a single step, for the price of one forward pass per encoder — the negatives cost nothing because they are just the other cells of a matrix you already computed. This is also SigLIP's target: the softmax must normalize across the whole row, so every GPU needs every other GPU's embeddings, and that all-gather is what caps batch size on real hardware. Sigmoid loss scores each cell independently, so the coupling disappears.

---

### LLaVA Stage 1 and Stage 2 Training

**Stage 1 — Feature Alignment**:
```
Frozen:  ViT-L/14 (CLIP image encoder)
         Llama-2-7B / Vicuna-13B (LLM backbone)
Trained: 2-layer MLP projection (vision -> LLM hidden size)

Data:    LLaVA-CC3M-Pretrain-595K (image-caption pairs)
         Format: [image] -> "caption text"
Task:    Predict the caption token by token; gradients flow only to MLP
Result:  MLP learns to map ViT patch embeddings into LLM's embedding space
Compute: ~6 hours on 8xA100 80GB GPUs
```

**Put simply.** "Both expensive models are bolted shut; the only thing learning in Stage 1 is a two-matrix adapter that translates ViT-speak into LLM-speak."

The reason six hours on 8xA100 is enough becomes obvious once you count the trainable parameters. Almost nothing is being trained.

| Symbol | What it is |
|--------|------------|
| `1024` | ViT-L/14 output width — the size of each patch embedding leaving the frozen encoder |
| `4096` | LLM hidden size — the width the backbone expects for every input token |
| 2-layer MLP | `Linear(1024 -> 4096)` then `Linear(4096 -> 4096)`, with an activation between |
| `in x out` | Weight-matrix parameter count for one Linear layer |
| `+ out` | The bias vector, one number per output dimension |
| "Frozen" | Weights receive no gradient update; they still run forward, still cost VRAM for activations |

**Walk one example.** Count every parameter that actually moves in Stage 1:

```
  layer 1 :  1024 x 4096 + 4096  =   4,198,400
  layer 2 :  4096 x 4096 + 4096  =  16,781,312
                                    ----------
  projector total                =  20,979,712    (~21M parameters)

  share of a 7B LLM backbone     =  21M / 7B     =  0.30%
  fp16 checkpoint on disk        =  20,979,712 x 2 bytes  =  42 MB
```

**What the freeze actually buys.** Training 0.30% of the stack means the optimizer state (Adam keeps roughly two extra copies per trainable weight) covers 21M parameters instead of 7B, which is the difference between a projector that fits in a rounding error of GPU memory and a full fine-tune. It also makes the artifact portable: a 42 MB file is the entire vision capability, swappable onto the same frozen backbone. The cost of the freeze is that the LLM never learns to *interpret* visual tokens more skilfully — it can only receive whatever the MLP can express — which is exactly why Stage 2 has to unfreeze it.

**Stage 2 — Instruction Tuning**:
```
Frozen:  ViT-L/14 (still frozen)
Trained: MLP projection + LLM backbone (unfrozen)

Data:    LLaVA-Instruct-150K + COCO captions + ScienceQA (~665K examples)
         Format: [image] [SYSTEM] [USER: question] [ASSISTANT: answer]
Task:    Follow instructions about images; answers are diverse (yes/no, counts,
         detailed descriptions, multi-step reasoning)
Result:  Model can answer arbitrary questions about images
Compute: ~20 hours on 8xA100 80GB GPUs
```

---

### BROKEN -> FIX: Wrong Tool for Visual Reasoning

**BROKEN — Using CLIP for scene description:**

```python
# BROKEN: Attempting to use CLIP for image description
from transformers import CLIPProcessor, CLIPModel
import torch
from PIL import Image

model = CLIPModel.from_pretrained("openai/clip-vit-large-patch14")
processor = CLIPProcessor.from_pretrained("openai/clip-vit-large-patch14")

image = Image.open("complex_scene.jpg")
# WRONG: CLIP cannot generate text — it has no autoregressive decoder
# This code will fail: CLIP only produces embedding vectors, not text
inputs = processor(images=image, return_tensors="pt")
image_features = model.get_image_features(**inputs)  # [1, 512] embedding
# image_features is a 512-dimensional vector. There is no .generate() method.
# You cannot ask CLIP "what is happening in this image?" and get a sentence.
# CLIP is an encoder-only model. It does not generate. Period.
description = model.generate(...)  # AttributeError: CLIPModel has no generate()

# ALSO WRONG: using CLIP for fine-grained spatial questions
texts = ["person holding umbrella in top-right", "person holding umbrella in bottom-left"]
inputs = processor(text=texts, images=image, return_tensors="pt", padding=True)
outputs = model(**inputs)
logits = outputs.logits_per_image  # [1, 2]
# CLIP's global [CLS] embedding cannot distinguish spatial location.
# Both texts will score similarly because CLIP has no spatial awareness.
# The similarity scores are meaningless for spatial grounding.
```

**Why CLIP fails here:**
- CLIP is a dual encoder producing a single global image embedding (the [CLS] token).
- It has no autoregressive decoder — it cannot generate free-form text.
- Its global pooling discards spatial information — "top-right" vs "bottom-left" produces nearly identical embeddings.
- CLIP is designed for retrieval (does this image match this description?) not for reasoning or generation.

**FIX — Use LLaVA for rich scene understanding and spatial reasoning:**

```python
# FIX: Use LLaVA-1.5 for scene description and spatial VQA
from transformers import LlavaNextProcessor, LlavaNextForConditionalGeneration
import torch
from PIL import Image

# Load LLaVA-1.6 (LLaVA-NeXT) for better spatial reasoning
processor = LlavaNextProcessor.from_pretrained("llava-hf/llava-v1.6-mistral-7b-hf")
model = LlavaNextForConditionalGeneration.from_pretrained(
    "llava-hf/llava-v1.6-mistral-7b-hf",
    torch_dtype=torch.float16,
    device_map="auto"
)

image = Image.open("complex_scene.jpg")

# Describe the entire scene
conversation = [
    {
        "role": "user",
        "content": [
            {"type": "image"},
            {"type": "text", "text": "Describe everything happening in this photograph in detail."}
        ],
    },
]
prompt = processor.apply_chat_template(conversation, add_generation_prompt=True)
inputs = processor(images=image, text=prompt, return_tensors="pt").to(model.device)

with torch.inference_mode():
    output = model.generate(**inputs, max_new_tokens=200, do_sample=False)

response = processor.decode(output[0], skip_special_tokens=True)
# Returns: "The photograph shows a busy street market in the evening.
#           In the foreground, a vendor is selling fresh produce from a wooden cart.
#           In the top-right corner, two people are sharing an umbrella in the rain..."

# Spatial question — LLaVA can handle this; CLIP cannot
spatial_conversation = [
    {
        "role": "user",
        "content": [
            {"type": "image"},
            {"type": "text", "text": "What is the person in the top-right corner of the image holding?"}
        ],
    },
]
# LLaVA's patch tokens (196+) preserve spatial information that the LLM can attend to.
# It can distinguish top-right from bottom-left. CLIP cannot.
```

**Decision rule:**
- Use CLIP when: fast image-text similarity at scale, zero-shot image classification, embedding-based image retrieval, building image vector indexes.
- Use LLaVA/GPT-4V when: generating descriptions, answering questions about image content, spatial reasoning, counting objects, OCR in images, document understanding.

---

### VQA Inference Latency (Reference Numbers)

| Operation | Hardware | Latency |
|-----------|----------|---------|
| CLIP image embedding (single image) | A10G GPU | ~15ms |
| CLIP batch embedding (32 images) | A10G GPU | ~50ms total (~1.5ms/image) |
| LLaVA-1.5-7B VQA (image + 50-token response) | A10G GPU | ~800ms |
| LLaVA-1.5-7B batch (32 images) | A10G GPU | ~25ms/image throughput |
| GPT-4V API (single image query) | OpenAI endpoint | ~2–5s |
| Grounding DINO detection | A10G GPU | ~100ms |
| Florence-2 captioning | A10G GPU | ~200ms |

---

## 7. Real-World Examples

**Pinterest — "Shop the Look" visual search**: Pinterest uses CLIP-derived embeddings for its visual search product, enabling users to tap any object in a photo and find similar products. The system processes billions of image comparisons daily using ANN (approximate nearest neighbor) search over pre-computed CLIP embeddings. Key engineering challenge: efficiently indexing 10B+ product images with sub-100ms p99 latency using FAISS or HNSW indexes.

**Shopify — product similarity and deduplication**: Shopify uses CLIP embeddings to detect duplicate product listings across merchant catalogs and to power visual similarity search ("find products that look like this"). CLIP's zero-shot capability means no per-category fine-tuning is needed across Shopify's enormous taxonomy of product types. Embeddings are stored in a vector database and refreshed when the CLIP model is upgraded.

**OpenAI GPT-4V / GPT-4o — complex visual reasoning**: GPT-4V demonstrated capability at chart reading (extracting data points from bar charts), code screenshot debugging (identifying syntax errors from a screenshot), medical image analysis (describing radiological findings with appropriate caveats), and document question answering. GPT-4o extended this with audio input and reduced latency, enabling real-time visual assistance.

**Google Gemini 1.5 Pro — native multimodality**: Unlike LLaVA's post-hoc adapter approach, Gemini was trained from the ground up as a multimodal model. It natively handles multi-image sequences and video frames, processes up to 1M tokens including visual content, and demonstrates stronger multi-image reasoning (e.g., "what changed between these three product photos?").

**Anthropic Claude 3 / Claude 3.5 — document and form extraction**: Claude's vision capability is particularly strong on text-heavy images — scanned forms, invoices, charts with labels, and diagrammatic content. Production users report that Claude outperforms GPT-4V on OCR accuracy within structured documents. This reflects different emphasis in training data composition.

**Grounding DINO + SAM — robotics and automation**: The combination of Grounding DINO (open-vocabulary text-conditioned detection) and SAM (Segment Anything Model) enables robotic systems to receive natural language commands like "pick the red mug" and produce precise object masks for grasp planning. This pipeline replaced earlier systems requiring per-category training data.

---

## 8. Tradeoffs

### Architecture Comparison

| Architecture | Latency | Best Task | Cost (self-host) | Max Resolution | Spatial Awareness |
|-------------|---------|-----------|-----------------|----------------|-------------------|
| CLIP dual encoder | Very fast (~15ms) | Retrieval, zero-shot classification | Low (7B-class GPU) | 224–336px fixed | None (global embedding) |
| SigLIP | Very fast (~15ms) | Same as CLIP, better accuracy | Low | 224–384px fixed | None |
| BLIP-2 / InstructBLIP | Medium (~500ms) | VQA, captioning | Medium (A10G) | 224–448px | Limited (Q-Former bottleneck) |
| LLaVA-1.5-7B | Medium (~800ms) | VQA, instruction following | Medium (A10G, ~$0.50/hr) | 336px | Good (full patch tokens) |
| LLaVA-1.6-7B | Slower (~1.2s) | Chart, document, high-res VQA | Medium (A10G) | Up to 1344px (tiled) | Very good |
| InternVL2-8B | Medium (~900ms) | VQA, multilingual | Medium | 448–1024px | Very good |
| GPT-4V | Slow (2–5s) | All tasks, best reasoning | High (API, $0.002+/image) | Up to 2048px | Excellent |
| Grounding DINO | Fast (~100ms) | Bounding box detection | Low | Variable | Exact (coordinates) |
| Florence-2-large | Medium (~200ms) | Unified: caption + ground + OCR | Low | Variable | Good |

### Self-Hosted vs API

| Factor | Self-Hosted (LLaVA-7B) | API (GPT-4V) |
|--------|------------------------|-------------|
| Cost at 10K images/day | ~$5–15/day (GPU rental) | ~$20–100/day |
| Latency | 0.8–1.2s (A10G) | 2–5s (network + API) |
| Customization | Full fine-tuning control | Limited (system prompt only) |
| Privacy | Full data control | Data sent to OpenAI |
| Maintenance | Model ops burden | Zero ops |
| Quality ceiling | Competitive on focused tasks | Higher for complex reasoning |

---

## 9. When to Use / When NOT to Use

**Use CLIP / SigLIP when:**
- Building an image search or retrieval system at scale (millions to billions of images)
- Zero-shot image classification across many categories (no training data needed per category)
- Fast visual filtering before a more expensive model (CLIP pre-filters, LLaVA re-ranks)
- Computing visual similarity scores for deduplication or recommendation
- Embedding images for downstream vector database storage

**Use LLaVA-1.5 / LLaVA-1.6 (self-hosted) when:**
- Production VQA system with cost constraints or data privacy requirements
- Domain-specific adaptation via fine-tuning (medical, industrial, satellite imagery)
- On-premises deployment required (healthcare, finance, defense)
- Chart or document understanding at medium scale
- Building a visual assistant where you control the model

**Use GPT-4V / GPT-4o when:**
- Maximum quality is required and volume is low (prototyping, internal tools)
- Complex multi-step visual reasoning with no room for error
- Multi-image or video frame reasoning
- Rapid prototyping without infrastructure burden
- Document analysis at low volume

**Use Grounding DINO / Florence-2 when:**
- Task requires bounding box output (robotics, inspection, annotation tools)
- Open-vocabulary detection across categories not known at training time
- Unified vision task (captioning + grounding + OCR in one model)

**NOT recommended:**
- CLIP for image description, VQA, or generation — wrong architecture, fundamentally impossible
- CLIP for spatial or counting tasks — global embedding loses all positional information
- LLaVA for high-throughput retrieval at 100M+ scale — CLIP is 50x faster and purpose-built
- GPT-4V for high-volume production without cost modeling — $10K+/month is easy to hit
- LLaVA-1.5 for document reading with small text at 336px — the downsampling destroys fine details; use LLaVA-1.6 or GPT-4V

---

## 10. Common Pitfalls

**1. Conflating CLIP and LLaVA failure modes**

CLIP does not hallucinate — it produces a similarity score, not generated text. If a CLIP-based system returns incorrect results, the cause is poor embedding alignment or distribution shift, not hallucination. LLaVA and GPT-4V can and do hallucinate: describing objects that are not present, misreading text, inventing details. These are completely different failure modes requiring completely different mitigations. Do not conflate them in production incident analysis.

**2. Ignoring ViT input resolution limits**

LLaVA-1.5 processes all images at 336x336 pixels. A high-resolution invoice, engineering diagram, or dense data table downsampled to 336px loses fine detail irreversibly. Characters become unreadable. Data points in charts become ambiguous. Mitigation: use LLaVA-1.6 with dynamic high-resolution tiling (processes up to 1344px), or route document-heavy workloads to GPT-4V or a dedicated OCR pipeline (PaddleOCR). Always test your actual image types, not just benchmark images.

**3. Fine-tuning the ViT encoder without sufficient data**

For domain adaptation (e.g., medical X-ray VQA), the instinct is to fine-tune all components including the ViT encoder. This requires 100K+ domain-specific image-text pairs to improve on ImageNet-pretrained ViT representations. With less data, tuning the ViT degrades performance by overwriting the general visual features without enough domain examples to replace them. Correct approach: freeze ViT, tune only the MLP projection and the LLM layers. The ViT's ImageNet/CLIP-learned features are remarkably general and transfer well even to medical images.

**4. Mixing CLIP and general-purpose text embedding spaces**

CLIP text embeddings and text-embedding-ada-002 (or text-embedding-3-small) embeddings are NOT in the same space and cannot be compared. CLIP text embeddings are optimized for image-text alignment, not text-text semantic similarity. A search pipeline that stores CLIP image embeddings and OpenAI text embeddings in the same vector index will produce nonsensical results. Use CLIP embeddings for both query text and indexed images in image retrieval; use a text-only embedding model for pure text retrieval.

**5. Treating academic benchmark accuracy as production performance**

A model that achieves 85% on VQAv2 may perform at 60% on your specific domain. VQAv2 consists of natural images with straightforward questions. A medical imaging VQA system, industrial defect detection VQA, or satellite imagery QA involves distribution shift that no published benchmark captures. Always evaluate on 200–500 domain-specific examples before committing to a model. Named benchmark accuracy is a starting filter, not a deployment decision.

**6. Skipping visual counter-question hallucination testing**

Standard VQA evaluation measures correctness on questions where the answer exists in the image. It does not measure hallucination rate — the tendency to confidently describe objects that are not there. Always test with negative questions: "is there a purple elephant in this image?" on images with no elephants. GPT-4V hallucination rate is lower than smaller open-source VLMs on negative questions, but all models fail some percentage of the time. Build a rejection test into your evaluation suite.

**7. Underestimating image token costs in API-based VLMs**

GPT-4V and GPT-4o charge per image based on resolution and tile count. A 1024x1024 image uses ~765 image tokens at high-detail mode in GPT-4o, costing approximately $0.002 per image beyond the base text token cost. At 1M images/day, this becomes $2,000/day from image tokens alone. Always calculate image token costs separately from text token costs when budgeting API-based VLM workloads.

**Stated plainly.** "An image is not a free attachment — it is a block of tokens that is charged, and that eats your context window, before the user has typed a single word."

The tile formula is the part people miss: cost scales with image *area*, so doubling both dimensions quadruples the token bill.

| Symbol | What it is |
|--------|------------|
| `512` | Tile side length. High-detail mode dices the image into 512px squares |
| `170` | Tokens charged per tile |
| `85` | Flat base cost per image, charged once regardless of size |
| "low" detail | Fixed 85 tokens, no tiles — the whole image as one coarse thumbnail |
| `765` | Total for a 1024x1024 image at high detail: 4 tiles plus the base |
| `980` | LLaVA-1.6's visual token count for one tiled image (5 x 196) |

**Walk one example.** Price a single request that carries ten 1024x1024 images:

```
  one image, high detail
    tiles     = (1024 / 512)^2 = 2 x 2   =    4
    tile cost = 4 x 170                  =  680 tokens
    base cost =                               85 tokens
    per image =                             765 tokens

  ten images in one request  = 10 x 765  = 7,650 image tokens
  at $2.50 per 1M input tokens           = $0.019 per request

  daily volume, 1M images   x $0.002     = $2,000/day from images alone
```

**The same tax appears self-hosted, as latency instead of dollars.** LLaVA-1.6 spends 980 visual tokens where LLaVA-1.5 spent 196 — `980 / 196 = 5x` — and those tokens sit at the front of every prompt, attended to on every generated token. Nothing was billed, but the prefill got 5x longer and the KV cache 5x bigger, which is precisely the ~1.5x latency increase quoted in Section 12. Whether the bill arrives from a vendor or from your own GPU, resolution is bought with tokens.

---

## 11. Technologies & Tools

### Core Models

| Model | Organization | HuggingFace ID | Best For |
|-------|-------------|----------------|---------|
| CLIP ViT-L/14 | OpenAI | `openai/clip-vit-large-patch14` | Image-text retrieval, zero-shot classification |
| SigLIP SO400M | Google | `google/siglip-so400m-patch14-384` | Improved CLIP, 384px input |
| BLIP-2 OPT-2.7B | Salesforce | `Salesforce/blip2-opt-2.7b` | Lightweight VQA, captioning |
| InstructBLIP | Salesforce | `Salesforce/instructblip-vicuna-7b` | Instruction-following VQA |
| LLaVA-1.5-7B | LLaVA team (UW) | `llava-hf/llava-1.5-7b-hf` | VQA, balanced quality/cost |
| LLaVA-1.5-13B | LLaVA team (UW) | `llava-hf/llava-1.5-13b-hf` | Higher quality VQA |
| LLaVA-1.6 Mistral | LLaVA team (UW) | `llava-hf/llava-v1.6-mistral-7b-hf` | High-res charts, documents |
| InternVL2-8B | OpenGVLab | `OpenGVLab/InternVL2-8B` | MMMU benchmark leader (open source) |
| MiniCPM-V 2.6 | ModelBest/Tsinghua | `openbmb/MiniCPM-V-2_6` | Edge deployment, 3B params |
| Grounding DINO | IDEA-Research | `IDEA-Research/grounding-dino-base` | Open-vocabulary detection |
| Florence-2-large | Microsoft | `microsoft/Florence-2-large` | Unified captioning + grounding + OCR |
| Qwen-VL-Chat | Alibaba | `Qwen/Qwen-VL-Chat` | Multilingual visual chat |

### Closed-Source APIs

| Model | Provider | Strength |
|-------|---------|----------|
| GPT-4V / GPT-4o | OpenAI | Best overall reasoning, multi-image |
| Gemini 1.5 Pro | Google | Native multimodality, video, 1M context |
| Claude 3.5 Sonnet (vision) | Anthropic | Document/OCR accuracy, structured extraction |

### Supporting Libraries

- `transformers` (HuggingFace): unified API for loading all models above
- `FAISS` (Meta): ANN index for CLIP embedding search at scale — index selection and PQ tradeoffs in [Embeddings & Similarity Search](../embeddings_and_similarity_search/README.md)
- `hnswlib`: alternative ANN library; good for in-memory use
- `PaddleOCR` (Baidu): dedicated OCR; outperforms VLMs for pure text extraction
- `open_clip` (LAION): open-source CLIP training and model zoo, including EVA-CLIP variants
- `timm` (HuggingFace): ViT backbone zoo used internally by many VLMs

### Benchmarks

| Benchmark | What It Measures |
|-----------|----------------|
| VQAv2 | General open-ended VQA on natural images |
| MMMU | College-level multi-discipline VQA (science, engineering, medicine) |
| SEED-Bench | 19K questions across 12 visual understanding dimensions |
| MMBench | Multilingual, multi-domain VQA with structured evaluation |
| TextVQA | VQA requiring OCR (text in images) |
| GQA | Compositional spatial reasoning QA |
| POPE | Hallucination benchmark (negative existence questions) |

**Cross-reference**: [`../multimodal_models/`](../multimodal_models/) — covers the broader multimodal landscape including audio modalities, video-specific architectures (VideoLLaMA, Video-LLaVA), and speech-vision integration not covered here.

---

## 12. Interview Questions with Answers

**Q: What is the single most important thing to understand about CLIP — what can it do and what can it absolutely NOT do?**
CLIP maps images and text into a shared embedding space and measures their similarity. It can answer "does this image match this text description?" with a cosine similarity score. It absolutely cannot generate text — it has no autoregressive decoder, no language generation head, and no way to produce a description, answer, or any free-form text. CLIP is a retrieval and classification model only. Candidates who confuse CLIP with a captioning model are demonstrating a fundamental architectural misunderstanding.

**Q: LLaVA and CLIP both use ViT encoders. Why can LLaVA answer spatial questions ("what's in the top-right corner?") while CLIP cannot?**
CLIP uses only the [CLS] token — a single global pooled embedding that aggregates the entire image into one vector, discarding all spatial structure. LLaVA passes all 196 patch token embeddings (for a 336px image with 24x24 patches) directly to the LLM. The LLM can attend to individual patch embeddings and learn position-dependent patterns during instruction tuning. The spatial information is present in the patches; CLIP throws it away, LLaVA preserves it.

**Q: Walk me through CLIP's InfoNCE contrastive loss. Why does a large batch size matter?**
For a batch of N image-text pairs, compute the NxN cosine similarity matrix scaled by temperature tau. Apply cross-entropy loss row-wise (each image should match its text among N candidates) and column-wise (each text should match its image among N candidates). Larger N means more negatives per positive, creating harder training signal. CLIP used N=32,768 — each positive is contrasted against 32,767 negatives. With N=64, the task is trivially easy; the model barely learns. SigLIP replaced the softmax with sigmoid loss to decouple training stability from batch size, enabling effective training at smaller batches.

**Q: What is visual grounding and which models support it? Why don't standard VQA models support it natively?**
Visual grounding is predicting bounding box coordinates for objects described by text queries. Standard VQA models output free-form text tokens from a language modeling head — they have no coordinate prediction head. Supporting grounding requires either: (1) a dedicated detection head (Grounding DINO), (2) special coordinate tokens that the model is trained to emit as text (Shikra, Qwen-VL), or (3) a unified architecture where outputs can be text or coordinates depending on task tokens (Florence-2). Models like LLaVA-1.5 cannot ground unless explicitly fine-tuned with coordinate-annotated data.

**Q: How would you fine-tune LLaVA for a medical imaging use case? What do you freeze and why?**
Freeze the ViT encoder completely — CLIP's ImageNet-pretrained features transfer well to medical images (edges, textures, shapes are universal). Optionally freeze the first N LLM layers to preserve general language capabilities. Train: the MLP projection (always), upper LLM layers, and ideally the full LLM if you have 10K+ medical image-instruction pairs. Do not unfreeze ViT without 100K+ domain-specific images — you will degrade general visual features without enough data to replace them. Use low-rank adaptation (LoRA) on LLM layers if GPU memory is limited.

**Q: How does ViT patch embedding work for a 224x224 image with 16x16 patches? How many tokens does the LLM receive?**
224 / 16 = 14 patches per dimension, so 14 x 14 = 196 patches total. Each 16x16x3 patch (768 raw values) is flattened and linearly projected to d_model (e.g., 1024 for ViT-L). Position embeddings are added. The [CLS] token is prepended, giving 197 ViT output tokens. CLIP uses only [CLS]. LLaVA uses the 196 patch tokens (dropping [CLS]) and projects them through the MLP to the LLM's hidden size. The LLM input sequence is: [system prompt tokens] + [196 visual tokens] + [user text tokens].

**Q: What VQA benchmarks matter and what do they actually measure?**
VQAv2: 1.1M questions on MS-COCO images, open-ended answers, measures general visual QA. MMMU: 11.5K questions from college-level textbooks across 30 disciplines — science, medicine, engineering; tests complex visual reasoning that VQAv2 does not. SEED-Bench: 19K multiple-choice questions across 12 dimensions including spatial understanding and action prediction. POPE: specifically tests hallucination on negative existence questions ("is there a [non-existent object] in the image?") — the most important benchmark for production reliability. TextVQA: VQA requiring OCR capability; critical for document and receipt understanding use cases.

**Q: What is the cost and latency comparison between CLIP and GPT-4V for classifying 10 million images per day?**
CLIP: embed images once, cache embeddings, run cosine similarity at inference. At ~1.5ms/image throughput on A10G, 10M images = ~4.2 GPU-hours/day. A10G rents at ~$0.50/hour, so ~$2/day. Latency per query: 15ms for fresh embedding or sub-millisecond for ANN lookup against cached embeddings. GPT-4V: ~$0.002/image = $20,000/day for 10M images. Latency: 2–5 seconds per image. CLIP wins on cost (10,000x cheaper) and latency (100-300x faster) for classification at scale. GPT-4V wins on reasoning quality for complex questions that CLIP cannot handle at all.

**Q: What is SigLIP and why did Google develop it as an improvement over CLIP's training objective?**
SigLIP (Sigmoid Loss for Language-Image Pre-Training) replaces CLIP's softmax cross-entropy contrastive loss with sigmoid binary cross-entropy applied independently to each image-text pair. In CLIP's softmax formulation, the loss computation requires comparing all N pairs globally, creating gradient coupling across the batch and numerical instability at large scales. SigLIP treats each (image, text) pair as an independent binary classification (match vs no-match), removing the global normalization. This improves training stability, works better at smaller batch sizes (enabling training on more modest hardware), and achieves better performance at the same model size. SigLIP is now the default backbone for several state-of-the-art VLMs including PaliGemma.

**Q: How does LLaVA-1.6 handle high-resolution images differently from LLaVA-1.5, and why does it matter?**
LLaVA-1.5 downsamples all input images to 336x336 pixels before ViT processing. A 1200x800 document image is crushed to 336x336, making small text illegible and fine chart details unreadable. LLaVA-1.6 uses dynamic high-resolution tiling: the image is divided into up to 6 non-overlapping 336x336 tiles based on the original aspect ratio, plus one global thumbnail tile. Each tile is processed independently by ViT, producing 196 tokens per tile. For a 1344x336 image, this yields 4 tiles x 196 + 1 thumbnail x 196 = 980 visual tokens passed to the LLM. This roughly quadruples the effective visual resolution, dramatically improving chart reading, document QA, and OCR tasks at the cost of ~5x more visual tokens and ~1.5x higher LLM latency.

**Q: What is the Q-Former in BLIP-2 and what problem does it solve?**
The Q-Former is a lightweight transformer module with a fixed set of learnable query tokens (32 by default). The query tokens attend to image patch embeddings via cross-attention, extracting a compressed fixed-length visual representation. The Q-Former bridges a frozen ViT encoder and a frozen LLM (OPT or FlanT5) without requiring either to be modified. The problem it solves: the ViT produces 196 variable-length patch tokens; the LLM expects a fixed-length prefix. Q-Former acts as a learned information bottleneck, compressing 196 image tokens into 32 query outputs. The disadvantage is that this compression loses spatial detail. LLaVA's MLP approach avoids this by passing all patch tokens directly.

**Q: When would you choose InternVL2 over LLaVA for production deployment?**
InternVL2 consistently outperforms LLaVA-1.6 on MMMU (college-level reasoning) and competitive benchmarks, particularly for multilingual tasks and complex document understanding. Choose InternVL2 when: (1) MMMU-class complex visual reasoning is required, (2) multilingual image content is common (InternVL2 was trained on more multilingual data), (3) you need higher accuracy at 8B parameters than LLaVA-1.6 provides, or (4) your benchmark evaluation shows InternVL2 wins on your domain. Choose LLaVA when: (1) a larger ecosystem of fine-tuning tooling and community resources matters, (2) you are already using Llama-family LLMs and want architectural consistency, or (3) your evaluation shows equivalent performance at lower cost.

**Q: What is Florence-2 and how does it differ architecturally from CLIP and LLaVA?**
Florence-2 (Microsoft) is a unified vision foundation model that handles multiple vision tasks with a single model by prepending task-specific prompt tokens to the input: `<CAPTION>`, `<DETAILED_CAPTION>`, `<OD>` (object detection), `<GROUNDING_CAPTION>`, `<OCR>`. The architecture is a DaViT image encoder feeding a transformer encoder-decoder. Unlike CLIP (retrieval only, no generation), Florence-2 generates text or structured coordinates. Unlike LLaVA (requires a large LLM backbone), Florence-2 handles grounding and OCR in a compact model (232M or 771M parameters) without a separate 7B LLM. Use Florence-2 when you need captioning + grounding + OCR in a single lightweight deployment without the cost of a 7B LLM.

**Q: How does OCR capability emerge in VLMs and when should you use a dedicated OCR tool instead?**
OCR capability in VLMs like GPT-4V and Claude 3 emerges from training on large amounts of document images paired with their text content (e.g., PDFs with embedded text used as supervision signal). The VLM learns to read text in images as part of its general visual understanding. Use VLMs for OCR when: the OCR task is combined with understanding (e.g., "what does this form say and fill in this template?"), when layout understanding matters, or when the text is embedded in a complex visual context. Use dedicated OCR tools (PaddleOCR, Tesseract, AWS Textract) when: accuracy is paramount for structured document extraction, you need bounding boxes for each word, throughput is high, or cost must be minimized. PaddleOCR outperforms most VLMs on pure text extraction accuracy from scans.

**Q: How do you handle multi-image inputs in GPT-4V, and what is the token cost?**
GPT-4V and GPT-4o accept multiple images in a single API call by including multiple image objects in the messages array. Each image is processed independently by the vision encoder and its tokens are interleaved with text tokens at the position where the image appears in the conversation. Token cost per image depends on resolution and detail setting: at "low" detail, a fixed 85 tokens regardless of size; at "high" detail, the image is divided into 512px tiles and each tile costs 170 tokens plus a base 85 tokens. A 1024x1024 image at high detail costs 765 tokens. With 10 images per query and high detail, expect 7,650+ image tokens per request. For GPT-4o at $2.50/1M input tokens, 10 high-detail images = ~$0.019 in image tokens alone per query.

**Q: What is the difference between image captioning and VQA architecturally, and why does VQA require instruction tuning?**
Image captioning produces a fixed-style description of image content without external input: Image -> Encoder -> Decoder -> "A dog runs on a beach." The output distribution is narrow and predictable. VQA requires conditioning on an arbitrary natural language question: (Image, Question) -> Answer. The question can ask about counts, spatial relationships, text in the image, object attributes, comparisons — vastly broader output space. Architecturally both use similar encoder-decoder or LLM adapter designs, but VQA requires the model to have learned to follow diverse question patterns and produce appropriately structured answers. This is why instruction tuning (Stage 2 in LLaVA) is essential: pre-training on image-caption pairs gives image understanding but not the ability to answer arbitrary questions. Instruction tuning on visual QA pairs teaches the question-answering behavior explicitly.

---

## 13. Best Practices

**Match architecture to task from the start**: CLIP for retrieval and classification at scale; LLaVA for VQA and instruction-following generation; Grounding DINO for bounding box prediction; Florence-2 for unified lightweight vision tasks. Trying to force CLIP into a generation task or LLaVA into a high-throughput retrieval pipeline will either fail architecturally or destroy cost efficiency.

**Evaluate on domain-specific examples before committing**: Academic benchmark scores (VQAv2, MMMU) are necessary but not sufficient. Collect 200–500 examples representative of your actual production inputs and evaluate all candidate models before selection. Medical images, satellite imagery, industrial defect photos, financial charts — these all deviate significantly from benchmark distributions.

**Cache CLIP embeddings for static catalogs**: If your image catalog is largely static (product photos, knowledge base diagrams), pre-compute and cache all embeddings. Pinterest-style systems embed images once and serve billions of queries from a vector index with sub-millisecond ANN lookup. Do not re-embed on every query.

**Freeze ViT when fine-tuning**: For VLM domain adaptation, freeze the ViT encoder unless you have 100K+ domain-specific image-text pairs. The ViT's pretrained features generalize well. Tuning the MLP projection and LLM layers is sufficient for most domain adaptations and requires far less data and compute.

**Use LLaVA-1.6 for document and chart tasks, not LLaVA-1.5**: The 336px limit in LLaVA-1.5 is a hard constraint that will silently degrade accuracy on any document with small text. LLaVA-1.6's dynamic tiling costs 4–5x more visual tokens but is necessary for reliable document understanding.

**Test hallucination explicitly**: Include negative-existence questions in your evaluation suite (POPE benchmark methodology). Ask the model about objects that are definitely not in the image. Measure false positive rate. Set acceptance criteria before deployment. Smaller open-source VLMs hallucinate at higher rates than GPT-4V on POPE, though the gap is closing with instruction tuning improvements.

**Build a two-stage pipeline for cost-sensitive VQA at scale**: CLIP first (fast, cheap, ~15ms) to filter candidates or classify the query type, then LLaVA or GPT-4V only for queries that genuinely require generation. This can reduce expensive VLM calls by 60–80% in many production workloads.

**Monitor embedding distribution drift**: CLIP embeddings computed against one model checkpoint are incompatible with embeddings from an updated checkpoint. When upgrading CLIP versions, re-embed the entire catalog. Track embedding cosine similarity distributions over time to detect input distribution shift in production (a sudden shift in average similarity often indicates a new type of query or image content).

---

## 14. Case Study

### Visual Product Search at 50M Product Scale

**Problem**: An e-commerce platform (similar to Shopify's merchant ecosystem) has 50 million product images. A customer uploads a photo of a product they want to find or buy similar items to. The system must: (1) retrieve the top-10 visually similar products, (2) generate a text description of the uploaded product, and (3) rank results by combined visual similarity + text relevance.

---

#### Requirements

- **Functional**: Image upload -> top-10 similar products + uploaded item description
- **Scale**: 50M indexed product images; 500K search queries/day (peak 2K QPS)
- **Latency**: p99 < 500ms end-to-end (including image upload, embedding, retrieval)
- **Freshness**: new products added within 5 minutes of merchant upload
- **Cost**: target < $500/day total infrastructure cost
- **Accuracy**: top-10 precision@10 > 0.7 on a 5K human-labeled evaluation set

---

#### Architecture Decision: CLIP vs LLaVA for Each Subtask

**Subtask 1 — Retrieve visually similar products**: Use **CLIP**, not LLaVA.
- 50M products require ANN search over a pre-computed embedding index. CLIP embeds a query image in ~15ms and queries a FAISS/HNSW index in ~10ms. Total: ~25ms.
- LLaVA cannot produce an embedding for ANN comparison — it generates text, not a fixed embedding suitable for similarity search. Using LLaVA here is architecturally incompatible.
- Choose CLIP ViT-L/14 (or SigLIP SO400M for better accuracy at 384px).

**Subtask 2 — Generate description of uploaded product**: Use **LLaVA-1.5-7B** (self-hosted), not CLIP.
- CLIP cannot generate text. LLaVA-1.5-7B generates a natural language product description in ~800ms.
- At 500K queries/day with ~800ms LLM latency, need ~5 concurrent A10G GPUs for description generation.
- Alternative: GPT-4V API at ~$0.002/image = $1,000/day for 500K queries — exceeds budget.
- Use LLaVA self-hosted: ~$15/day for 5x A10G (spot instances), well within budget.
- Prompt: "Describe this product: material, color, style, category, notable features. Be concise."

**Subtask 3 — Multi-modal ranking of top-10 results**: Combine CLIP visual similarity score with BM25/bi-encoder text relevance score.
```
final_score = alpha * clip_similarity + (1-alpha) * text_relevance
alpha = 0.7  (tuned on labeled evaluation set)
```
Text relevance: embed LLaVA-generated description using a text-only embedding model (e.g., text-embedding-3-small), then compute cosine similarity against product catalog text embeddings. Note: CLIP text embeddings are NOT used here for text-text comparison — use a text-only model for text-text similarity.

---

#### Embedding Storage at 50M Scale

```
50M products x 768-d float32 = 50M x 768 x 4 bytes = 153.6 GB raw embeddings

Storage options:
  FAISS IVF-PQ (Product Quantization):
    Compression: 768-d float32 -> 96 bytes PQ code
    50M x 96 bytes = 4.8 GB (32x compression)
    Query latency: 10–15ms at 50M scale
    Accuracy: ~2% precision@10 loss vs exact search (acceptable)

  HNSW (hnswlib):
    No compression; stores full vectors
    50M x 768 x 4 = 153 GB RAM requirement — too expensive
    Query latency: sub-1ms but memory cost prohibitive at this scale

  Recommendation: FAISS IVF_HNSW_PQ hybrid or Milvus/Weaviate vector DB
    -> Tiered storage: hot embeddings in GPU memory, cold on SSD
    -> Serve ~5M most-queried products from GPU VRAM, rest from SSD
```

**What this actually says.** "An embedding index is just rows x dimensions x bytes-per-number, and Product Quantization wins by shrinking the third term from 4 bytes per dimension to a fraction of a byte."

Nothing about vector search is mysterious at the storage layer. The whole IVF-PQ decision is this one multiplication run twice.

| Symbol | What it is |
|--------|------------|
| `50M` | Row count — one embedding per indexed product image |
| `768` | Dimensions per embedding, fixed by the encoder's projection width |
| `4 bytes` | Size of one float32 number. The uncompressed per-dimension cost |
| `96 bytes` | Size of the entire PQ code that replaces all 768 floats for one vector |
| PQ | Product Quantization: split the vector into sub-vectors, replace each with a learned centroid ID |
| precision@10 | Fraction of the returned top-10 that are genuinely relevant — the accuracy PQ trades away |

**Walk one example.** Same 50M catalog, priced both ways:

```
  float32, uncompressed
    50,000,000 x 768 x 4 bytes = 153,600,000,000 bytes = 153.6 GB

  IVF-PQ codes
    50,000,000 x 96 bytes      =   4,800,000,000 bytes =   4.8 GB

  compression = (768 x 4) / 96 = 3072 / 96 = 32x smaller
  paid for with ~2% precision@10 loss vs exact search
```

**Why 32x is the decision, not 4.8 GB.** The absolute numbers matter less than which memory tier each lands in: 153.6 GB does not fit a single commodity host and forces a sharded, multi-node HNSW deployment; 4.8 GB fits in one server's RAM with room to spare, and even inside GPU VRAM for the hot shard. The 2% precision loss buys the collapse of an entire distributed system into one process. That is the trade to state out loud in an interview — PQ is bought for the operational simplification, and the accuracy is the invoice.

---

#### System Architecture

```
User Upload
    |
    v
Image Upload Service (S3 + CDN)
    |
    +------------+------------+
    |                         |
    v                         v
CLIP Embedding Service    LLaVA Description Service
(GPU: A10G x 2)           (GPU: A10G x 5)
    |                         |
    v                         |
ANN Search (FAISS)            |
50M indexed embeddings        |
    |                         |
    v                         v
Top-100 candidates    Product description text
    |                         |
    +------------+------------+
                 |
                 v
         Multi-Modal Ranker
         (CLIP score + text score)
                 |
                 v
            Top-10 Results + Description
                 |
                 v
            API Response to User
```

---

#### Re-Embedding Strategy When CLIP Model Is Upgraded

Model upgrades (e.g., CLIP ViT-L/14 -> SigLIP SO400M for 8% better precision@10) require re-embedding the entire 50M product catalog. Old and new embeddings are incompatible — cosine similarity between old-model image embedding and new-model text embedding is meaningless.

**Strategy: dual-index rollout**

```
1. Start background re-embedding job: process 50M products at ~1500 images/sec
   (2x A10G GPUs dedicated to batch embedding)
   Total time: 50M / 1500 = ~9.3 GPU-hours = ~5 hours wall time

2. Write new embeddings to a parallel index (index_v2) without touching index_v1

3. Shadow mode: route 5% of traffic to index_v2, compare precision@10 vs index_v1
   on a sampled labeled set — confirm the upgrade actually improves quality

4. Canary rollout: 5% -> 20% -> 50% -> 100% over 48 hours
   Monitor: precision@10, latency, error rate

5. Delete index_v1 after 2 weeks of stable v2 production

6. Cost: ~$5 in GPU time for re-embedding 50M images
```

---

#### Latency Budget Breakdown

```
Target: p99 < 500ms end-to-end

Component                  p50    p99
----------------------------------------
Image upload (CDN)          20ms   80ms
CLIP embedding (A10G)       15ms   25ms
ANN search (FAISS, 50M)     10ms   20ms
LLaVA description (A10G)   600ms  950ms
Multi-modal ranking          2ms    5ms
API serialization + network  5ms   20ms
----------------------------------------
Total (sequential)         652ms  1100ms  <- exceeds budget!

Optimization: parallelize CLIP+ANN and LLaVA
  CLIP embedding + ANN search:  25ms p50 / 45ms p99
  LLaVA description (parallel): 600ms p50 / 950ms p99  <- critical path
  Wait for both, then rank:      5ms p50 / 10ms p99
  Total:                        630ms p50 / 960ms p99

Still exceeds 500ms p99 target. Options:
  (a) Async UX: return top-10 visual results immediately from CLIP (~100ms),
      stream LLaVA description as it generates (progressive disclosure)
  (b) Smaller LLaVA model: LLaVA-1.5-7B on A100 (faster) -> ~500ms p99
  (c) Smaller description model: LLaVA-3B or MiniCPM-V for 200ms generation
  (d) Cache descriptions for products appearing in results frequently
      (top 1M products cover ~80% of queries by power law)

Recommended: (a) + (d) combination
  -> CLIP results displayed in ~100ms
  -> Cached descriptions serve ~80% of results instantly
  -> Remaining 20% stream in asynchronously
```

---

#### Cost Model

```
Infrastructure (daily):
  CLIP embedding service: 2x A10G spot = ~$0.80/day
  LLaVA description service: 5x A10G spot = ~$2.00/day
  FAISS index server (64GB RAM, 50M embeddings in PQ): ~$3/day
  Vector DB managed service alternative: Milvus cloud ~$8/day
  Storage (S3, 50M images avg 200KB): ~$100/month = ~$3.30/day
  CDN egress: ~$2/day at 500K queries
  Total: ~$11-20/day

At GPT-4V for descriptions instead:
  500K images/day x $0.002 = $1,000/day  <- 50-90x more expensive

Self-hosted LLaVA is the correct choice at this scale.
```

---

#### Interview Discussion Points

This case study demonstrates several key architectural decisions:

1. **Why CLIP for retrieval and LLaVA for generation** — the tasks have different requirements. CLIP's fixed embeddings enable ANN search; LLaVA's autoregressive decoder enables description generation. Using the wrong architecture for either task is a system design error.

2. **Vector index design at 50M scale** — full float32 vectors are too large; PQ compression achieves 32x reduction with acceptable accuracy loss. This is a standard production trade-off.

3. **Re-embedding strategy** — model upgrades require complete re-indexing with a dual-index canary rollout. This is a production operational concern that academic treatments of VLMs ignore entirely.

4. **Latency budget decomposition** — the naive sequential architecture fails the SLA; parallelization and async UX are necessary. This demonstrates that VLM latency is often the bottleneck in multi-component systems.

5. **Cost at scale** — self-hosted LLaVA at $20/day vs GPT-4V at $1,000/day for 500K queries/day shows why architecture selection has direct business impact, not just technical merit.
