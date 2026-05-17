# Design an Image Classification Pipeline at Scale

## Problem Statement

Design a production image classification system for an e-commerce platform that automatically categorizes product images into 1,000 categories (e.g., "Men's Running Shoes", "Women's Handbag - Leather"). Sellers upload 1M new product images per day. Each uploaded image must be classified within 5 seconds for the seller to see the result. At steady state, the system must serve 100K classification requests per second (catalog browsing, recommendation systems consuming category labels) with less than 50ms P99 latency. Target accuracy: 95% top-1 on a balanced test set, 98% top-5.

Constraints:
- 1M images/day training pipeline ingestion
- 100K QPS inference, <50ms P99 latency
- 95% top-1 accuracy, 98% top-5 accuracy on 1000 categories
- New category addition without full retraining (few-shot via prototypical networks)
- Drift detection: catch distribution shift within 24 hours

---

## Architecture Overview

```
  DATA PIPELINE
  ┌──────────────────────────────────────────────────────────────────┐
  │  Seller Upload (S3)                                              │
  │       │                                                          │
  │       v                                                          │
  │  Image Validation (PIL check: corrupt, too small <64px, NSFW)   │
  │       │                                                          │
  │       v                                                          │
  │  Preprocessing Worker (Ray, 500 workers)                         │
  │  - Resize to 224x224 (EfficientNet input)                        │
  │  - Normalize: mean=[0.485,0.456,0.406] std=[0.229,0.224,0.225]  │
  │  - Store preprocessed in S3 + metadata in PostgreSQL             │
  │       │                                                          │
  │  Training Data (DVC versioned):                                  │
  │  - 10M labeled images (historical)                               │
  │  - 1M new/day                                                    │
  │  - 70% train / 15% val / 15% test split (stratified by category) │
  └──────────────────────────────────────────────────────────────────┘

  TRAINING PIPELINE (weekly full retrain + nightly fine-tune)
  ┌──────────────────────────────────────────────────────────────────┐
  │  Training Cluster: 8x A100 (80GB) GPUs                          │
  │                                                                  │
  │  EfficientNet-B3 (pretrained ImageNet-21k)                       │
  │  - Replace final classifier: 1536 → 1000 classes                │
  │  - Phase 1 (5 epochs): freeze backbone, train classifier only    │
  │  - Phase 2 (20 epochs): unfreeze all, lower LR (1e-4 → 1e-5)   │
  │                                                                  │
  │  Distributed Training (PyTorch DDP):                            │
  │  - 8 GPUs, effective batch size 256 (32/GPU)                    │
  │  - Mixed precision: BF16 (A100 native)                          │
  │  - Gradient accumulation: 4 steps (effective batch 1024)        │
  │                                                                  │
  │  Schedule: WarmupCosineAnnealing                                 │
  │  - Warmup: 5 epochs, LR 1e-6 → 1e-4                            │
  │  - Cosine decay: 20 epochs, 1e-4 → 1e-6                        │
  │                                                                  │
  │  Regularization:                                                 │
  │  - Label smoothing epsilon=0.1                                   │
  │  - Mixup alpha=0.2 (blend two training images + labels)          │
  │  - Dropout 0.3 before classifier head                            │
  │                                                                  │
  │  Checkpoints → MLflow → Best val accuracy model selected         │
  └──────────────────────────────────────────────────────────────────┘

  EXPORT & OPTIMIZATION
  ┌──────────────────────────────────────────────────────────────────┐
  │  PyTorch model                                                   │
  │       │                                                          │
  │       ├──> ONNX export (opset 17)                               │
  │       │         │                                                │
  │       │         v                                                │
  │       │    TensorRT optimization (FP16, INT8 calibration)        │
  │       │    Throughput: ~8000 images/sec per GPU                  │
  │       │                                                          │
  │       └──> ONNX Runtime (CPU, for non-GPU nodes)                │
  │            Throughput: ~200 images/sec per vCPU                  │
  └──────────────────────────────────────────────────────────────────┘

  SERVING INFRASTRUCTURE (100K QPS, <50ms P99)
  ┌──────────────────────────────────────────────────────────────────┐
  │  Load Balancer (NGINX)                                           │
  │       │                                                          │
  │       v                                                          │
  │  API Gateway (FastAPI, async)                                    │
  │  - Image URL → download + decode (S3 presigned URL or base64)   │
  │  - MD5 hash → Redis cache lookup (TTL 7 days)                   │
  │  - Cache HIT (~40% of requests): return cached result           │
  │  - Cache MISS: forward to TorchServe                            │
  │       │                                                          │
  │       v                                                          │
  │  TorchServe Cluster (20 GPU nodes, K8s HPA)                     │
  │  - Workers per GPU: 4 (concurrent model instances)              │
  │  - Dynamic batching: max_batch=64, max_delay=5ms                │
  │  - Model version A/B routing (shadow mode for new models)        │
  │       │                                                          │
  │       v                                                          │
  │  Response: {category_id, category_name, confidence, top5}       │
  └──────────────────────────────────────────────────────────────────┘

  MONITORING & DRIFT DETECTION
  ┌──────────────────────────────────────────────────────────────────┐
  │  Per-request metrics → Kafka → Flink → Prometheus/Grafana        │
  │                                                                  │
  │  Accuracy monitoring:                                            │
  │  - 100 images/day human-labeled by QA team                      │
  │  - Expected accuracy 95%; alert if <92% over 3 consecutive days  │
  │                                                                  │
  │  Distribution drift (pixel-level):                              │
  │  - Track channel mean/std per hour                               │
  │  - KS test vs baseline distribution; p<0.05 → alert             │
  │                                                                  │
  │  Confidence drift:                                               │
  │  - Track fraction of predictions with confidence <0.5           │
  │  - Spike (>2x baseline) indicates OOD data or model issue        │
  └──────────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

**EfficientNet-B3 over ResNet-50**: EfficientNet-B3 achieves 82.8% ImageNet top-1 accuracy vs ResNet-50's 76.0%, with fewer parameters (12M vs 25M) and lower inference latency (25ms vs 30ms on CPU). The compound scaling (jointly scale depth, width, resolution) makes it more efficient for fine-grained classification like product categories.

**Two-phase fine-tuning**: In phase 1, only the classifier head trains (backbone frozen). This prevents catastrophic forgetting of ImageNet features and establishes a good initialization for the head in 5 epochs. Phase 2 unfreezes all layers with a much lower LR (1e-5) to gently adapt the backbone. Skipping phase 1 leads to 2-3% accuracy drop.

**Dynamic batching in TorchServe**: At 100K QPS with 20 GPU nodes, each node sees 5K RPS. Without batching, GPU utilization is <10% (each inference uses only a fraction of GPU compute). Dynamic batching accumulates requests for up to 5ms and processes them together (batch 64). GPU utilization rises from 10% to 80%, reducing cost 4x. The 5ms batching delay is acceptable within the 50ms budget.

**MD5 hash caching**: Identical product images are re-submitted frequently (catalog exports, seller re-uploads). MD5 hash of the raw image bytes as cache key catches exact duplicates. Redis with 7-day TTL, ~40% cache hit rate measured in production — reduces GPU load by 40%.

**ONNX export for cross-platform serving**: PyTorch model is exported to ONNX once and served via TensorRT (NVIDIA GPUs, peak throughput), ONNX Runtime (CPU fallback, new region without GPU budget), and CoreML (edge devices via conversion). Single training produces artifacts for all platforms.

**Few-shot category addition**: Adding a new category without full retraining uses prototypical networks — compute the mean embedding of 5-20 example images of the new category using the frozen backbone, store as the "prototype." At inference, classify by nearest prototype in embedding space. Accuracy is ~85% (vs 95% for trained categories) but sufficient for new category bootstrapping.

---

## Implementation

### EfficientNet Fine-Tuning (PyTorch)

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms, models
from torchvision.models import EfficientNet_B3_Weights
import numpy as np
from pathlib import Path
from PIL import Image
import time


class ProductImageDataset(Dataset):
    """Dataset loading product images with augmentation."""

    TRAIN_TRANSFORMS = transforms.Compose([
        transforms.RandomResizedCrop(224, scale=(0.7, 1.0)),
        transforms.RandomHorizontalFlip(),
        transforms.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.2, hue=0.1),
        transforms.RandomGrayscale(p=0.05),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225],
        ),
    ])

    VAL_TRANSFORMS = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225],
        ),
    ])

    def __init__(
        self,
        image_paths: list[str],
        labels: list[int],
        split: str = "train",
    ) -> None:
        self.image_paths = image_paths
        self.labels = labels
        self.transform = (
            self.TRAIN_TRANSFORMS if split == "train" else self.VAL_TRANSFORMS
        )

    def __len__(self) -> int:
        return len(self.image_paths)

    def __getitem__(self, idx: int) -> tuple[torch.Tensor, int]:
        img = Image.open(self.image_paths[idx]).convert("RGB")
        return self.transform(img), self.labels[idx]


def mixup_data(
    x: torch.Tensor,
    y: torch.Tensor,
    alpha: float = 0.2,
) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor, float]:
    """Mixup augmentation: blend two random training examples."""
    if alpha > 0:
        lam = float(np.random.beta(alpha, alpha))
    else:
        lam = 1.0
    batch_size = x.size(0)
    index = torch.randperm(batch_size, device=x.device)
    mixed_x = lam * x + (1 - lam) * x[index]
    y_a, y_b = y, y[index]
    return mixed_x, y_a, y_b, lam


def mixup_criterion(
    criterion: nn.Module,
    pred: torch.Tensor,
    y_a: torch.Tensor,
    y_b: torch.Tensor,
    lam: float,
) -> torch.Tensor:
    return lam * criterion(pred, y_a) + (1 - lam) * criterion(pred, y_b)


def build_model(num_classes: int = 1000, dropout: float = 0.3) -> nn.Module:
    """EfficientNet-B3 with custom classifier head."""
    model = models.efficientnet_b3(weights=EfficientNet_B3_Weights.IMAGENET1K_V1)
    in_features = model.classifier[1].in_features  # 1536
    model.classifier = nn.Sequential(
        nn.Dropout(p=dropout),
        nn.Linear(in_features, num_classes),
    )
    return model


def train_epoch(
    model: nn.Module,
    loader: DataLoader,
    optimizer: torch.optim.Optimizer,
    criterion: nn.Module,
    device: torch.device,
    use_mixup: bool = True,
    grad_accum_steps: int = 4,
) -> dict[str, float]:
    model.train()
    total_loss = 0.0
    correct = 0
    total = 0

    optimizer.zero_grad()
    for step, (images, labels) in enumerate(loader):
        images, labels = images.to(device), labels.to(device)

        if use_mixup:
            images, y_a, y_b, lam = mixup_data(images, labels)
            with torch.autocast(device_type=device.type, dtype=torch.bfloat16):
                outputs = model(images)
                loss = mixup_criterion(criterion, outputs, y_a, y_b, lam)
        else:
            with torch.autocast(device_type=device.type, dtype=torch.bfloat16):
                outputs = model(images)
                loss = criterion(outputs, labels)

        loss = loss / grad_accum_steps
        loss.backward()

        if (step + 1) % grad_accum_steps == 0:
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            optimizer.zero_grad()

        total_loss += loss.item() * grad_accum_steps
        preds = outputs.argmax(dim=1)
        if not use_mixup:
            correct += (preds == labels).sum().item()
        total += labels.size(0)

    return {
        "loss": total_loss / len(loader),
        "accuracy": correct / total if not use_mixup else float("nan"),
    }


def two_phase_finetune(
    model: nn.Module,
    train_loader: DataLoader,
    val_loader: DataLoader,
    device: torch.device,
    num_classes: int = 1000,
) -> nn.Module:
    """Two-phase fine-tuning strategy."""
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)

    # Phase 1: train classifier head only (freeze backbone)
    for param in model.features.parameters():
        param.requires_grad = False
    optimizer_p1 = torch.optim.Adam(model.classifier.parameters(), lr=1e-3)

    print("Phase 1: training classifier head (5 epochs)")
    for epoch in range(5):
        metrics = train_epoch(
            model, train_loader, optimizer_p1, criterion, device, use_mixup=False
        )
        print(f"  Epoch {epoch+1}: loss={metrics['loss']:.4f}, "
              f"acc={metrics['accuracy']:.4f}")

    # Phase 2: unfreeze all, lower LR
    for param in model.parameters():
        param.requires_grad = True
    optimizer_p2 = torch.optim.Adam([
        {"params": model.features.parameters(), "lr": 1e-5},
        {"params": model.classifier.parameters(), "lr": 1e-4},
    ])
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer_p2, T_max=20 * len(train_loader)
    )

    print("Phase 2: full fine-tuning (20 epochs)")
    best_val_acc = 0.0
    for epoch in range(20):
        metrics = train_epoch(
            model, train_loader, optimizer_p2, criterion, device,
            use_mixup=True, grad_accum_steps=4
        )
        # Validate
        model.eval()
        correct, total = 0, 0
        with torch.no_grad():
            for images, labels in val_loader:
                images, labels = images.to(device), labels.to(device)
                outputs = model(images)
                correct += (outputs.argmax(1) == labels).sum().item()
                total += labels.size(0)
        val_acc = correct / total
        scheduler.step()

        print(f"  Epoch {epoch+1}: train_loss={metrics['loss']:.4f}, "
              f"val_acc={val_acc:.4f}")

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save(model.state_dict(), "best_model.pt")

    print(f"Best validation accuracy: {best_val_acc:.4f}")
    model.load_state_dict(torch.load("best_model.pt"))
    return model
```

### ONNX Export and TorchServe Handler

```python
import torch
import torch.nn as nn
import onnx
import onnxruntime as ort
import numpy as np


def export_to_onnx(
    model: nn.Module,
    output_path: str = "efficientnet_b3.onnx",
    opset_version: int = 17,
    batch_size: int = 64,
) -> None:
    """Export PyTorch model to ONNX with dynamic batching support."""
    model.eval()
    device = next(model.parameters()).device
    dummy_input = torch.randn(1, 3, 224, 224, device=device)

    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        opset_version=opset_version,
        input_names=["image"],
        output_names=["logits"],
        dynamic_axes={
            "image": {0: "batch_size"},    # dynamic batch dimension
            "logits": {0: "batch_size"},
        },
        do_constant_folding=True,  # fold constant operations for speed
    )

    # Verify ONNX model
    onnx_model = onnx.load(output_path)
    onnx.checker.check_model(onnx_model)
    print(f"ONNX model exported to {output_path}")

    # Benchmark ONNX Runtime
    sess_options = ort.SessionOptions()
    sess_options.intra_op_num_threads = 4
    sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    session = ort.InferenceSession(
        output_path,
        sess_options=sess_options,
        providers=["CUDAExecutionProvider", "CPUExecutionProvider"],
    )
    dummy_np = np.random.randn(batch_size, 3, 224, 224).astype(np.float32)
    start = time.time()
    for _ in range(100):
        session.run(["logits"], {"image": dummy_np})
    elapsed = (time.time() - start) / 100
    throughput = batch_size / elapsed
    print(f"ONNX Runtime: {elapsed*1000:.1f}ms per batch={batch_size}, "
          f"throughput={throughput:.0f} img/sec")


# TorchServe Handler (save as efficientnet_handler.py)
TORCHSERVE_HANDLER = '''
import torch
import torchvision.transforms as transforms
from ts.torch_handler.base_handler import BaseHandler
from PIL import Image
import io
import json
import base64


class EfficientNetHandler(BaseHandler):
    """Custom TorchServe handler for product image classification."""

    TRANSFORMS = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225],
        ),
    ])

    def preprocess(self, requests):
        """Decode images from base64 or bytes, apply transforms, batch."""
        images = []
        for req in requests:
            body = req.get("body", req.get("data", b""))
            if isinstance(body, str):
                body = base64.b64decode(body)
            img = Image.open(io.BytesIO(body)).convert("RGB")
            images.append(self.TRANSFORMS(img))
        return torch.stack(images).to(self.device)

    def inference(self, inputs):
        with torch.no_grad(), torch.autocast(device_type="cuda", dtype=torch.float16):
            logits = self.model(inputs)
        return torch.softmax(logits, dim=-1)

    def postprocess(self, probs):
        """Return top-5 predictions with confidence scores."""
        results = []
        for prob_row in probs:
            top5 = torch.topk(prob_row, k=5)
            results.append({
                "category_id": int(top5.indices[0]),
                "confidence": float(top5.values[0]),
                "top5": [
                    {"category_id": int(idx), "confidence": float(conf)}
                    for idx, conf in zip(top5.indices, top5.values)
                ],
            })
        return results
'''
```

### Drift Detection

```python
import numpy as np
from scipy import stats
import redis
import json


class ImageDriftDetector:
    """
    Monitor for distribution shift in incoming images.
    Tracks pixel-level statistics and prediction confidence.
    """

    BASELINE_KEY = "drift:baseline"
    CURRENT_WINDOW_KEY = "drift:current:{hour}"

    def __init__(self, redis_client: redis.Redis) -> None:
        self.redis = redis_client

    def record_prediction(
        self,
        image_array: np.ndarray,  # (C, H, W) float32 [0,1]
        confidence: float,
        hour: int,
    ) -> None:
        """Record per-channel statistics and confidence for drift monitoring."""
        stats_dict = {
            "r_mean": float(image_array[0].mean()),
            "g_mean": float(image_array[1].mean()),
            "b_mean": float(image_array[2].mean()),
            "r_std": float(image_array[0].std()),
            "g_std": float(image_array[1].std()),
            "b_std": float(image_array[2].std()),
            "confidence": confidence,
        }
        key = self.CURRENT_WINDOW_KEY.format(hour=hour)
        self.redis.rpush(key, json.dumps(stats_dict))
        self.redis.expire(key, 7200)  # 2-hour window

    def check_drift(self, hour: int, alpha: float = 0.05) -> dict[str, bool]:
        """KS test comparing current window to baseline."""
        baseline_raw = self.redis.lrange(self.BASELINE_KEY, 0, -1)
        current_raw = self.redis.lrange(
            self.CURRENT_WINDOW_KEY.format(hour=hour), 0, -1
        )
        if len(baseline_raw) < 100 or len(current_raw) < 100:
            return {"insufficient_data": True}

        baseline = [json.loads(x) for x in baseline_raw]
        current = [json.loads(x) for x in current_raw]

        alerts = {}
        for metric in ["r_mean", "g_mean", "b_mean", "confidence"]:
            baseline_vals = [d[metric] for d in baseline]
            current_vals = [d[metric] for d in current]
            ks_stat, p_value = stats.ks_2samp(baseline_vals, current_vals)
            alerts[f"{metric}_drift"] = p_value < alpha

        # Confidence spike detection: low_conf_rate > 2x baseline
        baseline_low_conf = sum(1 for d in baseline if d["confidence"] < 0.5) / len(baseline)
        current_low_conf = sum(1 for d in current if d["confidence"] < 0.5) / len(current)
        alerts["confidence_spike"] = current_low_conf > 2 * baseline_low_conf

        return alerts
```

---

## ML Components Used

| Component | Technology | Role |
|-----------|-----------|------|
| Backbone | EfficientNet-B3 (timm / torchvision) | ImageNet pretrained feature extractor |
| Training Framework | PyTorch DDP (DistributedDataParallel) | Multi-GPU training, 8x A100 |
| Mixed Precision | BF16 (torch.autocast) | 2x training speedup, A100 native |
| Augmentation | torchvision transforms + Mixup | Regularization, reduce overfitting |
| Model Registry | MLflow | Versioning, metric tracking, artifact storage |
| Export | torch.onnx.export (opset 17) | Cross-platform deployment artifact |
| Inference Runtime | TorchServe + TensorRT | Dynamic batching, GPU serving |
| Caching | Redis (MD5 key, 7-day TTL) | Duplicate image request deduplication |
| Drift Detection | KS test + confidence monitoring | Distribution shift alerting |
| Data Versioning | DVC (Data Version Control) | Reproducible training datasets |

---

## Tradeoffs and Alternatives

| Decision | Chosen | Alternative | Reasoning |
|----------|--------|-------------|-----------|
| Model architecture | EfficientNet-B3 | ViT-B/16, ResNet-50 | EfficientNet-B3: best accuracy/latency tradeoff at 12M params; ViT needs more data |
| Fine-tuning strategy | Two-phase (freeze → unfreeze) | Full fine-tuning from epoch 1 | Two-phase: +2-3% accuracy, prevents head instability destroying pretrained features |
| Inference server | TorchServe | Triton Inference Server, Ray Serve | TorchServe: native PyTorch integration, simpler ops; Triton better for multi-framework |
| Precision | BF16 | FP16, INT8 | BF16: larger dynamic range than FP16 (fewer NaN issues), A100-native; INT8 needs calibration |
| Caching key | MD5 of raw image bytes | Perceptual hash (pHash) | MD5 catches exact duplicates (most common case); pHash catches near-duplicates at 3x cost |
| New category strategy | Prototypical networks (few-shot) | Full retraining | Prototypical: deploy in hours with 5 examples; full retrain takes 2 days |

---

## Interview Discussion Points

**How do you handle the 100K QPS requirement with <50ms P99?**
Three mechanisms work together: (1) Redis caching with MD5 key deduplicates ~40% of requests, reducing effective QPS to 60K. (2) Dynamic batching in TorchServe accumulates requests for 5ms and processes as a batch of 64, increasing GPU utilization from 10% to 80%. With 20 GPU nodes each handling 3K effective QPS, total capacity is 60K QPS with headroom. (3) Horizontal auto-scaling (K8s HPA on GPU utilization metric): scale from 20 to 40 nodes in 3 minutes if load spikes. The 50ms budget breaks down as: network 5ms + preprocessing 5ms + Redis lookup 1ms + batching wait 5ms + GPU inference 15ms + response serialization 2ms = 33ms P50, with 17ms tail latency headroom.

**How do you detect and respond to model drift in production?**
Two-tier monitoring: (1) Pixel-level drift — track per-channel mean/std of incoming images hourly. KS test against the baseline distribution (collected during model launch). PSI > 0.2 or p < 0.05 triggers a Slack alert and automatic shadow deployment of a candidate retrained model. (2) Quality drift — 100 images/day are sampled and human-labeled by QA. If rolling 7-day accuracy drops below 92%, trigger emergency retraining. Confidence distribution monitoring provides an early warning signal: if the fraction of predictions with confidence <0.5 doubles, this indicates OOD (out-of-distribution) inputs before ground truth labels are even available.

**How do you add a new product category without retraining the full model?**
Prototypical network approach: (1) Collect 5-20 representative images of the new category from the seller's catalog. (2) Pass them through the EfficientNet backbone (frozen), extract the penultimate 1536-dim embedding. (3) Compute the mean of these embeddings — this is the "prototype" for the new class. (4) At inference time for the new category, compute cosine similarity between the query image embedding and all stored prototypes; the nearest prototype determines the class. Accuracy is ~85% vs 95% for trained classes, but deployment is instant. After accumulating 100+ examples, schedule a fine-tuning run to promote the class to a full trained head.
