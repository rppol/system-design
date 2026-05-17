# Computer Vision

## Deep Dive Files

| File | Topic | Q&As |
|------|-------|------|
| [object_detection.md](object_detection.md) | Two-stage, one-stage, anchor-free detectors; mAP; NMS | 15+ |
| [image_segmentation.md](image_segmentation.md) | Semantic, instance, panoptic; U-Net; SAM; Dice loss | 15+ |
| [vision_transformers.md](vision_transformers.md) | ViT, DeiT, Swin, CLIP; patch embeddings; attention | 15+ |
| [self_supervised_vision.md](self_supervised_vision.md) | SimCLR, MoCo, DINO, MAE, BYOL; linear probing | 15+ |

---

## 1. Concept Overview

Computer Vision (CV) is the field of enabling machines to interpret and understand visual information from images and video. It encompasses a hierarchy of tasks: image classification assigns a label to an entire image; object detection localizes and labels multiple objects within it; segmentation partitions every pixel into semantic or instance categories; and generative modeling synthesizes realistic imagery.

Modern CV is almost entirely driven by deep convolutional neural networks (CNNs) and, increasingly, vision transformers (ViTs). The ImageNet Large Scale Visual Recognition Challenge (ILSVRC) catalyzed the field: AlexNet (2012) dropped top-5 error from ~26% to 15%, ResNet (2015) reached superhuman classification accuracy, and ViT (2020) demonstrated that pure self-attention scales better than convolutions given enough data.

---

## 2. Intuition

Think of a CNN as a hierarchy of increasingly abstract feature detectors: the first layers detect edges and textures; middle layers detect parts (wheels, eyes, windows); deeper layers detect whole objects. A vision transformer instead treats an image as a sequence of non-overlapping patches and lets every patch attend to every other patch, learning global context from the first layer.

The core mental model:
- **Why it matters**: autonomous vehicles, medical imaging, industrial inspection, content moderation, and AR/VR all require pixel-level understanding.
- **Key insight**: pretraining on a large labeled dataset (ImageNet, ~1.28M images, 1000 classes) and then fine-tuning on a smaller target dataset transfers general visual representations cheaply — this is transfer learning and it is the default starting point for any CV project.

---

## 3. Core Principles

**Spatial hierarchy**: convolutions with small kernels (3x3) stacked with pooling layers build increasingly large receptive fields, allowing the network to detect patterns at multiple scales.

**Translation equivariance**: convolutional filters slide across the spatial dimensions, so the same feature detector fires regardless of where an object appears — a property CNNs get for free but ViTs must learn from data.

**Normalization**: batch normalization (2015) made training deep networks stable by normalizing intermediate activations. Layer normalization is preferred in transformers.

**Residual connections**: skip connections (ResNet, 2015) allow gradients to flow directly through identity mappings, enabling networks of 50–152+ layers without vanishing gradients.

**Data augmentation**: artificially expanding the training distribution via random crops, flips, color jitter, and mixup reduces overfitting. Standard augmentation pipelines (RandAugment, TrivialAugmentWide) can close the gap between small and large datasets.

**Transfer learning**: pretrain on large labeled corpus → fine-tune on target task. Freezing early layers and training only the final classifier (linear probing) is the minimal intervention; full fine-tuning with a low learning rate (1e-4 to 1e-5) achieves higher accuracy on sufficiently large target datasets.

---

## 4. Types / Architectures / Strategies

### Classification

| Model | Year | Top-1 ImageNet | Params | Key Innovation |
|-------|------|----------------|--------|----------------|
| AlexNet | 2012 | 63.3% | 60M | Deep CNN, ReLU, dropout |
| VGG-16 | 2014 | 74.4% | 138M | Uniform 3x3 convolutions |
| ResNet-50 | 2015 | 76.1% | 25M | Residual connections |
| EfficientNet-B7 | 2019 | 84.3% | 66M | Compound scaling |
| ViT-H/14 | 2020 | 88.6% | 632M | Pure self-attention on patches |
| ConvNeXt-XL | 2022 | 87.8% | 350M | Modernized CNN with ViT tricks |

### Detection

| Model | Type | Speed | mAP COCO | Notes |
|-------|------|-------|----------|-------|
| Faster R-CNN | Two-stage | ~5 fps GPU | 37.4 | High accuracy, slower |
| YOLOv8-L | One-stage | ~100 fps GPU | 52.9 | Real-time standard |
| DETR | Transformer | ~28 fps GPU | 42.0 | No NMS, Hungarian matching |
| FCOS | Anchor-free | ~40 fps GPU | 44.7 | No anchor hyperparameters |

### Segmentation

| Model | Task | Metric | Notes |
|-------|------|--------|-------|
| FCN | Semantic | mIoU | First fully convolutional approach |
| DeepLab v3+ | Semantic | mIoU ~89% ADE20K | Atrous convolution, ASPP |
| U-Net | Semantic | mIoU | Medical imaging standard |
| Mask R-CNN | Instance | AP mask | Adds mask branch to Faster R-CNN |
| SAM / SAM 2 | Promptable | — | Foundation model for segmentation |

### Generation

| Model | Type | Metric | Notes |
|-------|------|--------|-------|
| StyleGAN3 | GAN | FID ~2.79 FFHQ | State-of-art face synthesis |
| Stable Diffusion | Diffusion | FID ~12.6 COCO | Latent diffusion, open weights |
| DALL-E 3 | Diffusion+LLM | — | Text-conditional, closed |

---

## 5. Architecture Diagrams

### CNN Feature Hierarchy

```
Input Image (224x224x3)
        |
   [Conv3x3 + BN + ReLU]  x2   → 112x112x64   (edges, gradients)
        |
   [MaxPool 2x2]
        |
   [Conv3x3 + BN + ReLU]  x3   → 56x56x128    (textures, corners)
        |
   [Conv3x3 + BN + ReLU]  x4   → 28x28x256    (parts: wheel, eye)
        |
   [Conv3x3 + BN + ReLU]  x6   → 14x14x512    (objects: car, face)
        |
   [GlobalAvgPool]              → 1x1x512
        |
   [FC + Softmax]               → num_classes
```

### ResNet Residual Block

```
  x ──────────────────────────────┐
  |                               |
  [Conv 3x3, BN, ReLU]            |  (identity shortcut)
  |                               |
  [Conv 3x3, BN]                  |
  |                               |
  [+] ←───────────────────────────┘
  |
  [ReLU]
  |
  output
```

### Transfer Learning Pipeline

```
Step 1: Pretrain on ImageNet (1.28M images, 1000 classes)
┌────────────────────────────────────────────────────────┐
│  Backbone (ResNet-50 / ViT-B/16)                       │
│  Feature extractor — learns general visual patterns    │
└────────────────────────────────────────────────────────┘

Step 2: Replace classifier head
┌────────────────────────────────────────────────────────┐
│  Backbone (frozen or low LR)  →  New FC (num_classes)  │
└────────────────────────────────────────────────────────┘

Step 3: Fine-tune on target dataset
  - Linear probe: freeze backbone, train head only (fast, few samples)
  - Full fine-tune: unfreeze all, LR 1e-4 (more data, more gain)
  - Layer-wise LR decay: deeper layers get lower LR (ViT best practice)
```

### CV Task Progression

```
Classification          Detection              Segmentation
┌──────────────┐        ┌──────────────┐       ┌──────────────┐
│              │        │  ┌──┐        │       │##############│
│    [Cat]     │   →    │  │  │ Cat    │  →    │##  Cat  #####│
│              │        │  └──┘        │       │##############│
└──────────────┘        └──────────────┘       └──────────────┘
  one label per           bounding box +         label per pixel
  whole image             class per object        (semantic/instance)
```

---

## 6. How It Works — Detailed Mechanics

### Standard Preprocessing Pipeline

```python
import torch
import torchvision.transforms as T
from torchvision.transforms import InterpolationMode
from PIL import Image
from typing import Callable

# ImageNet statistics — used for any ImageNet-pretrained model
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD  = [0.229, 0.224, 0.225]

def build_train_transform(img_size: int = 224) -> Callable:
    return T.Compose([
        T.RandomResizedCrop(img_size, scale=(0.08, 1.0),
                            interpolation=InterpolationMode.BICUBIC),
        T.RandomHorizontalFlip(p=0.5),
        T.ColorJitter(brightness=0.4, contrast=0.4,
                      saturation=0.4, hue=0.1),
        T.ToTensor(),
        T.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
    ])

def build_val_transform(img_size: int = 224,
                         resize_size: int = 256) -> Callable:
    # resize shorter edge to 256, then center-crop to 224
    return T.Compose([
        T.Resize(resize_size, interpolation=InterpolationMode.BICUBIC),
        T.CenterCrop(img_size),
        T.ToTensor(),
        T.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
    ])
```

### Fine-Tuning ResNet-50 with Layer-Wise LR

```python
import torch
import torch.nn as nn
from torchvision import models
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR

def build_finetuning_model(num_classes: int,
                            pretrained: bool = True) -> nn.Module:
    model = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V2
                             if pretrained else None)
    # Replace final fully-connected layer
    in_features: int = model.fc.in_features
    model.fc = nn.Linear(in_features, num_classes)
    return model

def build_optimizer(model: nn.Module,
                    base_lr: float = 1e-4,
                    head_lr: float = 1e-3) -> AdamW:
    # Separate parameter groups: backbone gets lower LR
    backbone_params = [p for name, p in model.named_parameters()
                       if "fc" not in name]
    head_params     = list(model.fc.parameters())
    return AdamW([
        {"params": backbone_params, "lr": base_lr},
        {"params": head_params,     "lr": head_lr},
    ], weight_decay=1e-4)

def train_one_epoch(model: nn.Module,
                    loader: torch.utils.data.DataLoader,
                    optimizer: AdamW,
                    device: torch.device) -> float:
    model.train()
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)
    total_loss = 0.0
    for images, labels in loader:
        images, labels = images.to(device), labels.to(device)
        optimizer.zero_grad()
        logits = model(images)
        loss = criterion(logits, labels)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()
        total_loss += loss.item()
    return total_loss / len(loader)
```

### Evaluation: Top-1 and Top-5 Accuracy

```python
import torch
from torch import Tensor

@torch.no_grad()
def evaluate(model: nn.Module,
             loader: torch.utils.data.DataLoader,
             device: torch.device) -> dict[str, float]:
    model.eval()
    top1_correct = top5_correct = total = 0

    for images, labels in loader:
        images, labels = images.to(device), labels.to(device)
        logits: Tensor = model(images)                # (B, C)
        _, top5_preds = logits.topk(5, dim=1)         # (B, 5)
        top1_preds = top5_preds[:, 0]

        top1_correct += (top1_preds == labels).sum().item()
        top5_correct += (top5_preds == labels.unsqueeze(1)
                         .expand_as(top5_preds)).any(dim=1).sum().item()
        total += labels.size(0)

    return {
        "top1": top1_correct / total,
        "top5": top5_correct / total,
    }
```

---

## 7. Real-World Examples

**Autonomous vehicles**: Tesla FSD uses a multi-camera vision-only stack. Each camera feed runs a backbone (similar to ViT) to produce bird's-eye-view feature maps. Detection heads localize vehicles, pedestrians, and lane markings at ~36 fps per camera.

**Medical imaging**: U-Net variants dominate radiology segmentation. A chest X-ray pneumonia detection model fine-tuned from DenseNet-121 on CheXpert achieves AUC ~0.91. The same backbone serves multiple pathology classifiers via multi-label heads.

**Content moderation**: Instagram/Meta runs EfficientNet-B5 inference to classify uploaded images for nudity, violence, and spam at ~50M images/day. Inference is batched on A10G GPUs; models serve from TorchServe behind an internal API.

**Industrial inspection**: PCB defect detection uses YOLO models fine-tuned on 10k–50k annotated boards. mAP@0.5 of 0.92+ is routinely achieved; false-negative rate below 0.1% is the production SLA.

---

## 8. Tradeoffs

| Dimension | CNN | Vision Transformer |
|-----------|-----|-------------------|
| Data efficiency | High (inductive bias) | Low (needs 14M+ images or pretraining) |
| Scaling behavior | Diminishing returns | Near-log-linear with compute |
| Throughput (A100) | ResNet-50: ~1200 img/s | ViT-B/16: ~900 img/s |
| Fine-tuning cost | Low (ImageNet init) | Medium (ImageNet-21k init) |
| Interpretability | Grad-CAM works well | Attention maps noisier |
| Long-range context | Requires deep stacking | Global from layer 1 |

| Augmentation | Benefit | Risk |
|--------------|---------|------|
| RandomResizedCrop | Scale invariance | Crops lose context |
| ColorJitter | Lighting robustness | Distorts color-critical tasks |
| Mixup / CutMix | Better calibration | Confusing labels for small datasets |
| RandAugment | Automated policy | Adds ~20% training time |

---

## 9. When to Use / When NOT to Use

**Use CNNs (ResNet, EfficientNet) when**:
- Dataset is small (< 100k images) — inductive bias compensates for data scarcity.
- Real-time inference on edge/mobile — MobileNetV3, EfficientNet-Lite.
- You need simple, well-understood architecture for production.

**Use ViTs when**:
- Dataset is large (> 1M images) or strong pretrained checkpoint is available.
- Task benefits from global context (dense prediction at scale, CLIP-style retrieval).
- You can afford larger GPU memory footprint.

**Do NOT use CV models when**:
- Input is structured tabular data — use gradient boosting or MLP.
- You have fewer than ~500 images and no similar pretrained model — collect more data first.
- Latency requirement is under 1ms on CPU — use classical feature matching (ORB, SIFT) or lightweight heuristics.

---

## 10. Common Pitfalls

**Pitfall 1: Forgetting to normalize with ImageNet statistics**
A team fine-tuned ResNet-50 on a medical dataset, replacing the preprocessing pipeline. They normalized to [0, 1] but forgot to apply ImageNet mean/std subtraction. Validation accuracy plateaued at 61% vs. 79% with correct normalization. The pretrained weights expect inputs in a specific distribution — always match the original preprocessing.

**Pitfall 2: Data leakage via augmentation**
Augmentations (especially MixUp) must be applied only to the training set. A junior engineer accidentally applied RandAugment inside the Dataset `__getitem__` without checking the split flag. Validation images received random crops, making validation loss artificially low and masking 6% overfitting.

**Pitfall 3: Ignoring class imbalance**
An industrial defect detection model trained on 99% normal / 1% defective boards hit 99% accuracy but zero recall on defects. Fix: weighted random sampler or focal loss (gamma=2). Always compute per-class metrics, never just global accuracy.

**Pitfall 4: Resizing strategy mismatch between train and val**
Training used `RandomResizedCrop(224)` (crops down to 8% of image area). Validation used `Resize(224)` without center crop. The effective receptive field statistics differed, costing ~1.5% top-1 accuracy. Standard practice: resize shorter edge to 256, center-crop to 224.

**Pitfall 5: Not freezing BatchNorm during fine-tuning**
When fine-tuning with a small batch (< 16) on a new domain, BatchNorm running statistics computed on the tiny batch degrade the pretrained statistics. Fix: call `model.eval()` on BN layers or use `freeze_bn()` to keep running mean/var frozen during the first few epochs.

---

## 11. Technologies & Tools

| Category | Tool | Notes |
|----------|------|-------|
| Framework | PyTorch 2.x | Default for research and production CV |
| Model zoo | torchvision, timm | timm has 700+ pretrained models |
| Detection | Ultralytics YOLOv8, Detectron2 | YOLOv8 for speed; Detectron2 for research |
| Segmentation | mmsegmentation, huggingface | SAM via `segment-anything` package |
| Training | PyTorch Lightning, Hugging Face Trainer | Reduces boilerplate |
| Data | Albumentations | Fast augmentation library (10x faster than torchvision) |
| Serving | TorchServe, Triton Inference Server | Triton for multi-model, multi-framework |
| Annotation | Label Studio, CVAT, Roboflow | CVAT for open-source; Roboflow for managed |
| Experiment tracking | Weights & Biases, MLflow | W&B standard in CV teams |
| Profiling | torch.profiler, NVIDIA Nsight | Find GPU bottlenecks |

---

## 12. Interview Questions with Answers

**Q: What is the difference between top-1 and top-5 accuracy on ImageNet?**
Top-1 accuracy counts a prediction as correct only if the highest-scoring class matches the ground truth. Top-5 accuracy counts it as correct if the ground truth appears anywhere in the top 5 predicted classes. Top-5 is more lenient and was reported alongside top-1 in early ImageNet papers because some classes are visually ambiguous (e.g., dog breeds). ResNet-50 achieves 76.1% top-1 and 92.9% top-5.

**Q: Why do we normalize images with ImageNet mean and std even when fine-tuning on a different dataset?**
Pretrained weights were optimized assuming inputs in that specific distribution. Applying the same normalization keeps the input statistics consistent with what the pretrained backbone expects, allowing the learned feature representations to transfer without disruption. Deviating from it forces the early layers to re-adapt, slowing convergence and typically reducing final accuracy.

**Q: What is the receptive field and why does it matter?**
The receptive field of a neuron is the region of the input image that influences its activation. Deeper neurons have larger receptive fields. For object detection, a neuron must have a receptive field at least as large as the objects it detects; for semantic segmentation, global context often improves boundary accuracy. Dilated (atrous) convolutions expand the receptive field without increasing parameters.

**Q: Explain residual connections and why they matter.**
A residual connection adds the input of a block directly to its output: `output = F(x) + x`. This means the block only needs to learn the residual `F(x)`, not the full mapping. During backpropagation, gradients flow directly through the identity path, avoiding vanishing gradients in deep networks. ResNet-50 (25M params) outperforms VGG-16 (138M params) because 50 layers of residual learning beat 16 layers of plain learning.

**Q: What is transfer learning and when does it fail?**
Transfer learning pretrained on ImageNet and fine-tunes on a target dataset, reusing learned visual features. It fails when the domain gap is too large — e.g., satellite imagery, medical histology, or infrared images have different low-level statistics than natural photos, so early layers may need retraining. It also fails when the target task structure is fundamentally different (e.g., counting rather than classification requires architectural changes).

**Q: How do you handle class imbalance in image classification?**
Three main approaches: (1) weighted random sampler — oversample minority classes so each batch is balanced; (2) weighted cross-entropy loss — assign higher loss weight to minority classes; (3) focal loss — down-weights easy examples dynamically, forcing the model to focus on hard minority examples. Always verify per-class recall in addition to global accuracy.

**Q: What is data augmentation and what are its limits?**
Data augmentation synthetically expands the training set by applying random transformations (crops, flips, color jitter, cutout). It regularizes the model and improves generalization. Its limits: augmentations must preserve label semantics (horizontal flip is invalid for digit recognition of 6/9; heavy crops may remove the object); too-strong augmentation on very small datasets can increase training noise rather than helping.

**Q: Compare CNNs and ViTs in terms of inductive bias.**
CNNs have strong inductive biases: local connectivity (nearby pixels are more related), weight sharing (same filter applied everywhere), and spatial hierarchy (pooling builds scale invariance). ViTs have almost no such inductive bias — every patch attends to every other patch from layer one, so they must learn spatial relationships from data. This makes ViTs data-hungry but gives them superior scaling behavior with large datasets or pretraining.

**Q: What is mAP and how is it computed for object detection?**
Mean Average Precision (mAP) is the standard detection metric. For each class: (1) rank all predicted boxes by confidence score; (2) compute precision and recall at each rank using IoU >= 0.5 (COCO uses 0.5:0.05:0.95) to determine TP vs FP; (3) compute Average Precision (AP) as the area under the precision-recall curve; (4) average AP across all classes. COCO mAP averages over 10 IoU thresholds from 0.5 to 0.95.

**Q: What is the difference between semantic and instance segmentation?**
Semantic segmentation assigns a class label to every pixel but does not distinguish between different instances of the same class — all cars are the same color. Instance segmentation assigns both a class label and a unique instance ID to each object, so two adjacent cars get different masks. Panoptic segmentation combines both: it produces instance-level masks for countable objects (things) and semantic labels for uncountable regions (stuff like sky, road).

**Q: What is FID (Frechet Inception Distance) and what does it measure?**
FID measures the quality and diversity of generated images by comparing the distribution of real and generated images in the feature space of Inception-v3. It computes the Frechet distance between two multivariate Gaussians fit to the Inception features. Lower FID is better. A FID of 0 means generated images are indistinguishable from real ones. StyleGAN3 achieves FID ~2.79 on FFHQ; Stable Diffusion achieves FID ~12.6 on COCO.

**Q: How do you deploy a computer vision model to production?**
Standard pipeline: export model to TorchScript or ONNX for portability; optimize with TensorRT for NVIDIA GPUs (typically 2-4x faster than PyTorch eager); serve via Triton Inference Server for multi-model batching; implement preprocessing on GPU using DALI or torchvision GPU transforms; monitor latency (P50/P99), throughput (imgs/sec), and accuracy drift on production samples. ResNet-50 achieves ~4ms on V100 with TorchScript and ~2ms with TensorRT FP16.

---

## 13. Best Practices

1. Always start from a pretrained checkpoint. Training from scratch on fewer than 500k images rarely outperforms transfer learning.
2. Match preprocessing exactly between training and inference — use the same resize/crop strategy, same normalization constants. Mismatches are a leading cause of accuracy degradation in production.
3. Use Albumentations for augmentation in detection/segmentation pipelines — it applies the same geometric transform to both image and annotations, preventing annotation drift.
4. Monitor per-class metrics, not just global accuracy. Class imbalance hides poor performance on minority classes.
5. Profile your data pipeline first. In most CV training runs, the bottleneck is CPU-side augmentation, not GPU. Use `num_workers >= 4` and `pin_memory=True`.
6. Use mixed precision (AMP) by default — `torch.autocast(device_type="cuda", dtype=torch.float16)` — for ~2x speed and ~2x memory reduction with negligible accuracy loss.
7. Apply label smoothing (epsilon=0.1) for classification to reduce overconfidence and improve calibration.
8. For fine-tuning transformers, use layer-wise learning rate decay (deeper layers get lower LR, e.g., multiplied by 0.65 per layer group) to prevent catastrophic forgetting.
9. Log confusion matrices and misclassified samples to W&B every epoch — patterns in errors drive the next iteration of data collection.
10. Use test-time augmentation (TTA) — average predictions over multiple augmented views — to get 0.5–1.5% free accuracy improvement at inference time.

---

## 14. Case Study

**Problem**: A retail company needs to classify ~20 product categories from shelf photos taken on mobile phones (varying lighting, angles, partial occlusion). They have 8,000 labeled images.

**Baseline approach**:
- EfficientNet-B3 pretrained on ImageNet (12M params, balanced accuracy/speed).
- Training transform: `RandomResizedCrop(300)`, `RandomHorizontalFlip`, `ColorJitter`, `Normalize(IMAGENET_MEAN, IMAGENET_STD)`.
- Validation transform: `Resize(320)`, `CenterCrop(300)`, `Normalize(...)`.
- AdamW, base LR 1e-4 (backbone), 1e-3 (head), weight decay 1e-4, 50 epochs, cosine schedule.
- Initial result: top-1 accuracy 74%.

**Iteration 1 — better augmentation**:
- Added RandAugment (magnitude=9, num_ops=2) and CutMix (alpha=1.0).
- Result: 78%.

**Iteration 2 — class imbalance fix**:
- Discovered 4 categories had < 200 images; others had 600+.
- Added WeightedRandomSampler and focal loss (gamma=2).
- Result: minority class recall went from 31% to 67%; macro-averaged F1 improved from 0.71 to 0.81.

**Iteration 3 — knowledge distillation**:
- Trained EfficientNet-B7 teacher (84.3% ImageNet top-1) for 100 epochs → 83% accuracy.
- Distilled into EfficientNet-B3 student using soft labels (temperature=4, alpha=0.7).
- Student reached 81% accuracy vs. 78% with hard labels, with no inference overhead.

**Production deployment**:
- Model exported to ONNX, optimized with TensorRT FP16.
- Deployed on AWS EC2 G4dn (T4 GPU), achieving 8ms end-to-end latency (preprocessing + inference + postprocessing).
- TorchServe handles batching; batch size 32 at 100 req/s with P99 latency 25ms.
- Accuracy monitored weekly via a stratified sample of 500 production images reviewed by QA.
