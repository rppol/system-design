# Convolutional Neural Networks

## 1. Concept Overview

A Convolutional Neural Network (CNN) is a neural network architecture that exploits three structural biases of spatial data: local connectivity (neurons connect to a small spatial region, not all inputs), weight sharing (the same filter is applied across all spatial locations), and translation equivariance (a feature detector learns to fire for a pattern regardless of where in the image it appears). These biases dramatically reduce the parameter count relative to fully connected layers and embed domain knowledge about the structure of images directly into the architecture.

CNNs learn a hierarchy of filters: early layers detect low-level features (edges, colors, textures), middle layers detect mid-level patterns (corners, curves, object parts), and later layers detect high-level semantic concepts (faces, wheels, text). This hierarchy makes CNNs the dominant architecture for computer vision tasks.

---

## 2. Intuition

One-line analogy: a CNN is like a flashlight scanning a document — the same small light (filter) slides across the entire page (feature map), looking for the same pattern everywhere, then a second flashlight looks for a different pattern, and so on.

Mental model: the network learns a stack of overlapping templates. At each layer, a set of learned filters is convolved with the input to produce a new set of feature maps — each map represents "how strongly does each location match this template?" Deeper filters combine outputs of shallower ones, building progressively richer descriptions.

Why it matters: CNNs power image classification, object detection, semantic segmentation, medical imaging, autonomous driving perception, and satellite imagery analysis. Transfer learning from ImageNet-pretrained CNNs is the most cost-effective starting point for the vast majority of vision tasks.

Key insight: parameter sharing is the core efficiency gain. A 3x3 filter applied to a 224x224 image has 9 parameters and covers the entire image in a single layer, versus a fully connected layer needing 224x224x9 = ~450K parameters for the same spatial coverage.

---

## 3. Core Principles

**Convolution operation**: a filter (kernel) of shape (K, K, C_in) slides over an input feature map. At each spatial position, it computes the dot product between the filter weights and the input patch, producing one scalar in the output feature map. Applying C_out different filters produces a feature map of shape (H_out, W_out, C_out).

**Output size formula**: given input size W, filter size F, padding P, stride S:
```
H_out = floor((H - F + 2P) / S) + 1
W_out = floor((W - F + 2P) / S) + 1
```

Common cases:
- 3x3 conv, P=1, S=1: output same as input (same padding)
- 3x3 conv, P=0, S=1: output shrinks by 2 each side
- 3x3 conv, P=1, S=2: output halves (halved spatial resolution)

**Receptive field**: the region of the input that influences a given neuron's output. Grows with depth. For a stack of K layers of 3x3 conv with stride 1: RF = 1 + 2*K. For strided convolutions, the receptive field grows faster: RF(k) = RF(k-1) + (kernel_size - 1) * product_of_all_previous_strides.

**Pooling**: reduces spatial dimensions to provide spatial invariance. Max pooling takes the maximum in each window (retains strongest activation). Average pooling takes the mean (smoother, often used in global pooling before FC layers).

**Skip connections (ResNet)**: add the input directly to the output of a block, bypassing the nonlinear transformations. Enables training of very deep networks by providing gradient highways that bypass vanishing gradient accumulation.

---

## 4. Types / Architectures / Strategies

| Architecture | Params | ImageNet Top-1 | Year | Key Innovation |
|-------------|--------|----------------|------|----------------|
| AlexNet | 60M | 63.3% | 2012 | First deep CNN, ReLU, Dropout |
| VGGNet-16 | 138M | 74.4% | 2014 | Small 3x3 convs stacked |
| ResNet-18 | 11M | 69.8% | 2015 | Skip connections |
| ResNet-50 | 25M | 76.2% | 2015 | Bottleneck blocks |
| ResNet-101 | 44M | 77.4% | 2015 | Deeper residual network |
| MobileNetV2 | 3.4M | 72.0% | 2018 | Depthwise separable convs |
| EfficientNet-B0 | 5.3M | 77.1% | 2019 | Compound scaling |
| EfficientNet-B7 | 66M | 84.4% | 2019 | Scaled up B0 |
| ConvNeXt-L | 197M | 87.5% | 2022 | Modernized ResNet |

**Standard Convolution vs Depthwise Separable Convolution (MobileNet):**

Standard: one kernel of shape (K, K, C_in) per output channel -> total ops: K^2 * C_in * C_out * H * W

Depthwise separable = depthwise (one filter per input channel: K^2 * C_in * H * W) + pointwise (1x1 conv: C_in * C_out * H * W). Total ops: K^2 * C_in + C_in * C_out per spatial position. Speedup factor: 1 / (1/C_out + 1/K^2), roughly 8-9x for 3x3 conv with large C_out.

**EfficientNet Compound Scaling**: jointly scale width (channels), depth (layers), and resolution (input size) with a fixed ratio. Given a resource budget multiplier phi: depth *= alpha^phi, width *= beta^phi, resolution *= gamma^phi with alpha*beta^2*gamma^2 ~= 2. EfficientNet-B7 uses phi=7 over B0 baseline.

**ResNet Bottleneck Block** (ResNet-50+): 1x1 conv (reduce channels) -> 3x3 conv -> 1x1 conv (expand channels). Reduces computation vs a naive 3x3-3x3 block. Identity shortcut for same-dimension blocks, 1x1 projection shortcut when dimensions change.

---

## 5. Architecture Diagrams

```
Standard CNN Pipeline:
Input (224x224x3)
    |
[Conv 7x7, stride 2, 64 filters] -> (112x112x64)   <- stem
    |
[MaxPool 3x3, stride 2]          -> (56x56x64)
    |
[Stage 1: 3x3 conv blocks, 64ch] -> (56x56x64)     <- early features: edges
    |
[Stage 2: stride 2, 128ch]       -> (28x28x128)    <- mid features: shapes
    |
[Stage 3: stride 2, 256ch]       -> (14x14x256)    <- high features: parts
    |
[Stage 4: stride 2, 512ch]       -> (7x7x512)      <- semantic features
    |
[Global Average Pooling]          -> (512,)
    |
[Linear(512, num_classes)]        -> (num_classes,)

ResNet Residual Block (BasicBlock, ResNet-18/34):
  x -----> [Conv 3x3] -> [BN] -> [ReLU] -> [Conv 3x3] -> [BN] -> (+) -> [ReLU] -> out
  |                                                                 ^
  +------------------------------------------------ identity ------+

ResNet Bottleneck Block (ResNet-50/101/152):
  x -> [Conv 1x1, reduce] -> [BN] -> [ReLU]
     -> [Conv 3x3]         -> [BN] -> [ReLU]
     -> [Conv 1x1, expand] -> [BN]
     -> (+ skip) -> [ReLU]

Depthwise Separable Conv (MobileNet):
  Input (H x W x Cin)
      |
  [Depthwise Conv: one 3x3 per channel] -> (H x W x Cin)   <- spatial features
      |
  [Pointwise Conv: 1x1, Cout filters]   -> (H x W x Cout)  <- channel mixing

  Params: 3*3*Cin + Cin*Cout  vs  3*3*Cin*Cout standard
  Ratio:  1/Cout + 1/9  (8-9x fewer params for typical Cout)
```

---

## 6. How It Works — Detailed Mechanics

### Building a ResNet-50 in PyTorch

```python
import torch
import torch.nn as nn
from torch import Tensor


class BottleneckBlock(nn.Module):
    expansion: int = 4  # output channels = planes * expansion

    def __init__(
        self,
        in_channels: int,
        planes: int,
        stride: int = 1,
        downsample: nn.Module | None = None,
    ) -> None:
        super().__init__()
        self.conv1 = nn.Conv2d(in_channels, planes, kernel_size=1, bias=False)
        self.bn1 = nn.BatchNorm2d(planes)
        self.conv2 = nn.Conv2d(planes, planes, kernel_size=3, stride=stride, padding=1, bias=False)
        self.bn2 = nn.BatchNorm2d(planes)
        self.conv3 = nn.Conv2d(planes, planes * self.expansion, kernel_size=1, bias=False)
        self.bn3 = nn.BatchNorm2d(planes * self.expansion)
        self.relu = nn.ReLU(inplace=True)
        self.downsample = downsample  # 1x1 conv projection when dimensions change

    def forward(self, x: Tensor) -> Tensor:
        identity = x
        out = self.relu(self.bn1(self.conv1(x)))
        out = self.relu(self.bn2(self.conv2(out)))
        out = self.bn3(self.conv3(out))
        if self.downsample is not None:
            identity = self.downsample(x)  # match channels/spatial dims
        out = out + identity  # skip connection: ADD input to output
        return self.relu(out)
```

### Output Size Calculation

```python
def conv_output_size(input_size: int, kernel: int, padding: int, stride: int) -> int:
    """Standard formula: floor((W - F + 2P) / S) + 1"""
    return (input_size - kernel + 2 * padding) // stride + 1

# Examples:
# 224x224 input, 7x7 conv, P=3, S=2 -> (224 - 7 + 6) / 2 + 1 = 112
# 112x112, 3x3 max pool, P=0, S=2  -> (112 - 3) / 2 + 1 = 56
```

### Transfer Learning Pattern

```python
import torchvision.models as models


def build_transfer_model(num_classes: int, freeze_backbone: bool = True) -> nn.Module:
    """
    Transfer learning from ImageNet-pretrained ResNet-50.
    Strategy: freeze backbone -> train head -> gradually unfreeze.
    """
    model = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V2)

    # Phase 1: freeze backbone, train only the new head
    if freeze_backbone:
        for param in model.parameters():
            param.requires_grad = False

    # Replace final FC layer (1000 ImageNet classes -> num_classes)
    in_features = model.fc.in_features  # 2048 for ResNet-50
    model.fc = nn.Sequential(
        nn.Dropout(p=0.3),
        nn.Linear(in_features, 512),
        nn.ReLU(),
        nn.Linear(512, num_classes),
    )
    # model.fc parameters have requires_grad=True by default (new module)
    return model


def unfreeze_layers(model: nn.Module, layers_to_unfreeze: list[str]) -> None:
    """
    Phase 2: gradually unfreeze later backbone layers.
    Unfreeze layer4 first, then layer3, etc. Use 10x lower LR for backbone.
    """
    for name, param in model.named_parameters():
        for layer_name in layers_to_unfreeze:
            if layer_name in name:
                param.requires_grad = True


# Differential learning rates: backbone LR = head LR / 10
def get_optimizer(model: nn.Module, head_lr: float = 1e-3) -> torch.optim.Optimizer:
    backbone_params = [p for n, p in model.named_parameters()
                       if "fc" not in n and p.requires_grad]
    head_params = [p for n, p in model.named_parameters()
                   if "fc" in n and p.requires_grad]
    return torch.optim.Adam([
        {"params": backbone_params, "lr": head_lr / 10},
        {"params": head_params,     "lr": head_lr},
    ])
```

### Depthwise Separable Convolution

```python
class DepthwiseSeparableConv(nn.Module):
    def __init__(self, in_channels: int, out_channels: int, stride: int = 1) -> None:
        super().__init__()
        self.depthwise = nn.Conv2d(
            in_channels, in_channels,
            kernel_size=3, stride=stride, padding=1,
            groups=in_channels,  # groups=in_channels means one filter per channel
            bias=False,
        )
        self.pointwise = nn.Conv2d(in_channels, out_channels, kernel_size=1, bias=False)
        self.bn = nn.BatchNorm2d(out_channels)
        self.relu = nn.ReLU6(inplace=True)  # ReLU6 as in MobileNet

    def forward(self, x: Tensor) -> Tensor:
        x = self.depthwise(x)
        x = self.pointwise(x)
        return self.relu(self.bn(x))
```

### Receptive Field Calculation

```python
def compute_receptive_field(layers: list[tuple[int, int]]) -> int:
    """
    layers: list of (kernel_size, stride) tuples.
    RF grows as: RF_k = RF_{k-1} + (kernel_size - 1) * stride_product_of_previous_layers
    """
    rf = 1
    stride_product = 1
    for kernel_size, stride in layers:
        rf += (kernel_size - 1) * stride_product
        stride_product *= stride
    return rf

# ResNet-50 stem + 4 stages example
# stem: 7x7 s2, maxpool 3x3 s2, then 3x3 convs in blocks
example_layers = [(7, 2), (3, 2), (3, 1), (3, 1), (3, 2), (3, 1), (3, 1), (3, 2)]
rf = compute_receptive_field(example_layers)
# Effective RF covers large spatial region by final stage
```

---

## 7. Real-World Examples

**ResNet-50 inference timing**: ~4ms on V100 GPU for a batch of 1, ~23ms on modern CPU (Intel Xeon). Used at Google, Meta, and Amazon for image moderation and product classification serving millions of requests per day.

**Transfer learning at minimal compute**: fine-tuning ResNet-50 on a 10-class custom dataset of 5,000 images takes ~20 minutes on a single GPU (freeze backbone, train head for 10 epochs) and achieves 85-92% accuracy. Training from scratch on the same dataset achieves ~60%.

**MobileNet in production**: deployed on-device for Android/iOS camera features. MobileNetV3-Small has 2.5M parameters and runs inference in ~5ms on a Qualcomm Snapdragon 888.

**EfficientNet for medical imaging**: EfficientNet-B4 trained on diabetic retinopathy images achieved 0.971 AUROC, surpassing human ophthalmologists (0.957). The compound scaling allowed matching accuracy with 3x fewer parameters than comparable ResNets.

---

## 8. Tradeoffs

| Approach | Params | Accuracy | Inference Speed | Memory | Best For |
|----------|--------|----------|----------------|--------|---------|
| ResNet-18 | 11M | Good | Fast (~2ms V100) | Low | Edge, real-time |
| ResNet-50 | 25M | Better | Medium (~4ms V100) | Medium | General purpose |
| ResNet-101 | 44M | Best of ResNets | Slower (~8ms V100) | High | Accuracy-critical |
| MobileNetV3 | 5M | Competitive | Very fast | Very low | Mobile/edge |
| EfficientNet-B0 | 5.3M | Excellent | Fast | Low | Efficiency-focused |
| EfficientNet-B7 | 66M | SOTA (2019) | Slow | High | Max accuracy |

| Transfer Learning Strategy | Data Size | Training Time | Final Accuracy |
|---------------------------|-----------|--------------|----------------|
| Freeze all, train head only | < 1K samples | Minutes | Moderate |
| Unfreeze top 2 stages | 1K-10K samples | Hours | Good |
| Fine-tune all layers | > 10K samples | Hours-Days | Best |
| Train from scratch | > 100K samples | Days | Best (large data) |

---

## 9. When to Use / When NOT to Use

**Use CNNs when:**
- Input has spatial structure (images, videos, spectrograms, 2D grid data)
- Translation equivariance is desirable (a cat is a cat wherever in the image)
- Large labeled datasets are available or ImageNet pretraining is applicable
- Inference latency requirements are tight (CNNs are well-optimized on GPU/NPU)

**Do NOT use CNNs when:**
- Input is tabular / unstructured (use MLP or tree ensembles)
- Sequence data without spatial structure (use RNN or Transformer)
- Long-range spatial dependencies dominate (Vision Transformer may outperform CNN)
- Very small dataset with no applicable pretrained model (simpler models generalize better)

**Use ResNet when:** accuracy is the primary concern and compute is available.
**Use MobileNet when:** latency and model size are constrained (mobile, edge, IoT).
**Use transfer learning when:** labeled data < 100K samples — almost always the right choice.

---

## 10. Common Pitfalls

**War story 1 — Wrong normalization statistics at inference:**
A team trained a ResNet on ImageNet-normalized images (mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]) but forgot to apply the same normalization to inference images. Accuracy dropped from 76% to 35%. The model had learned to expect normalized inputs; raw pixel values [0-255] are wildly out of distribution relative to training.

```python
from torchvision import transforms

# BROKEN: raw PIL image passed to model
def broken_predict(model, image):
    x = transforms.ToTensor()(image)  # scales to [0,1] but no normalization
    return model(x.unsqueeze(0))

# FIX: same normalization as training
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD  = [0.229, 0.224, 0.225]
inference_transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
])

def correct_predict(model, image):
    model.eval()
    with torch.no_grad():
        x = inference_transform(image).unsqueeze(0)
        return model(x)
```

**War story 2 — Forgetting 1x1 projection in ResNet shortcut:**
A custom ResNet implementation added a 3x3 conv that doubled channels (e.g., 64 -> 128) but kept the identity shortcut. Adding a (128,) output to a (64,) identity is a shape mismatch — PyTorch raised RuntimeError. Less obviously, if the developer silently zero-padded the shortcut instead of using a learned 1x1 projection, gradient flow through the shortcut was correct but the shortcut carried no learned information about the channel-doubled features, degrading accuracy by 3-5% on CIFAR-10.

**War story 3 — Batch size of 1 with BatchNorm during training:**
A segmentation model was trained with batch size 1 (each sample was a large 4K medical image). BatchNorm with batch size 1 has undefined variance (dividing by n-1=0). PyTorch does not error but produces NaN outputs. Training loss immediately became NaN. Fix: switch to GroupNorm (num_groups=32) or InstanceNorm which do not depend on the batch dimension.

**War story 4 — Transfer learning with frozen BN running stats:**
Fine-tuning a ResNet-50 on a thermal infrared dataset (very different pixel statistics from RGB ImageNet). BatchNorm running stats were from ImageNet (RGB mean ~128, std ~50). With frozen BN layers (common in Phase 1 transfer learning), the normalization applied completely wrong statistics to IR images. Fix: set `bn.track_running_stats = False` or use `model.train()` for BN layers even during head-only training on domain-shifted data.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `torchvision.models` | Pretrained ResNet, EfficientNet, MobileNet, ViT |
| `timm` (PyTorch Image Models) | 500+ pretrained vision models, easy fine-tuning |
| `albumentations` | Fast image augmentation (RandomCrop, CLAHE, GridDistortion) |
| `torchvision.transforms.v2` | Modern transform pipeline with consistent API |
| `torch.utils.data.DataLoader` | Multi-process data loading; num_workers=4, pin_memory=True |
| NVIDIA cuDNN | Auto-selects fastest conv algorithm (cudnn.benchmark=True) |
| ONNX Runtime | CPU/edge inference for exported models |
| TensorRT | GPU-optimized serving (INT8 quantization, layer fusion) |

```python
# Enable cuDNN auto-tuner for fixed-size inputs (finds fastest algorithm)
import torch.backends.cudnn as cudnn
cudnn.benchmark = True  # ~10-30% speedup for CNNs with fixed input size
# Note: disable if input sizes vary batch-to-batch (benchmark overhead dominates)
```

---

## 12. Interview Questions with Answers

**Q: What is the output size formula for a convolutional layer?**
The formula is floor((W - F + 2P) / S) + 1 where W is input size, F is filter size, P is padding, S is stride. For a 224x224 input with a 7x7 filter, stride 2, and padding 3: (224 - 7 + 6) / 2 + 1 = 112. This applies independently to height and width. Padding P = (F-1)/2 preserves spatial size when stride is 1 (same padding).

**Q: Why do CNNs use weight sharing and what advantage does it provide?**
Weight sharing means the same filter is applied at every spatial position. This reduces parameters dramatically — a 3x3 filter for 64-channel input and 64-channel output has 3*3*64*64 = 36,864 parameters regardless of input image size, versus a fully connected layer on a 224x224x64 input needing billions of parameters. Weight sharing also encodes the inductive bias that useful features (edges, textures) can appear anywhere in the image, improving generalization.

**Q: How do skip connections in ResNet solve the degradation problem?**
Without skip connections, very deep networks (>20 layers) achieve worse training accuracy than shallower ones — not because of overfitting, but because optimization becomes harder as depth increases. Skip connections provide a direct gradient path from the loss to early layers: the gradient can flow through the addition node without passing through any nonlinearity. The network also learns residual functions F(x) = H(x) - x rather than H(x) directly, which is easier to optimize since F(x)=0 is a trivial solution (identity mapping).

**Q: What is the difference between max pooling and average pooling? When would you use each?**
Max pooling takes the maximum value in each window, retaining the strongest activation and providing spatial invariance. Average pooling takes the mean, producing smoother, more spatially spread representations. Max pooling is preferred in early-to-mid network stages for feature detection. Global average pooling (GAP) is the standard replacement for flattening before classification heads (used in ResNet, EfficientNet) — it reduces each feature map to a single scalar, providing extreme translation invariance and cutting parameters dramatically vs flattening.

**Q: Explain depthwise separable convolutions and their computational advantage.**
A standard 3x3 conv over C_in channels with C_out filters costs K^2 * C_in * C_out multiply-adds per output location. Depthwise separable convolution splits this into depthwise (one 3x3 filter per input channel: K^2 * C_in cost) followed by pointwise (1x1 conv mixing channels: C_in * C_out cost). Total: K^2 * C_in + C_in * C_out. Reduction factor vs standard: 1/(1/C_out + 1/K^2). For K=3 and C_out=256, this is ~8.9x cheaper. MobileNet uses this throughout, achieving competitive accuracy at ~8x fewer multiply-adds.

**Q: What is transfer learning and why is it effective for computer vision?**
Transfer learning initializes a model with weights pretrained on a large dataset (usually ImageNet, 1.2M images, 1000 classes) then fine-tunes on a target dataset. It is effective because low-level features (edges, textures) learned from ImageNet are universal across vision tasks. Fine-tuning on 1,000 domain-specific images with a pretrained backbone achieves accuracy that would require 100,000+ images when training from scratch. The typical workflow: freeze backbone, train new head for several epochs, then gradually unfreeze later backbone layers with 10x lower learning rate.

**Q: What is the receptive field and why does it matter?**
The receptive field (RF) of a neuron is the region of the input image that can influence its output. For a single 3x3 conv with stride 1, RF=3x3. Stacking K such layers gives RF = 2K+1. For deep networks to make semantic predictions (e.g., "is there a car?"), they need RFs large enough to encompass the object. Larger kernels (7x7) or strided convolutions grow the RF faster, at the cost of more parameters or spatial resolution. Dilated (atrous) convolutions grow RF without losing spatial resolution, commonly used in semantic segmentation (DeepLab).

**Q: How does EfficientNet's compound scaling differ from ad-hoc scaling?**
Prior work scaled models by increasing one dimension independently: wider (more channels), deeper (more layers), or higher resolution. Compound scaling jointly increases all three dimensions with a fixed ratio (depth *= alpha^phi, width *= beta^phi, resolution *= gamma^phi) subject to alpha * beta^2 * gamma^2 ~= 2 (doubling FLOPs per phi increment). This respects the constraint that depth and resolution are more beneficial together — a deeper network benefits more from higher resolution input. Empirically, EfficientNet-B7 achieves better accuracy than ResNet-152 with 8.4x fewer parameters.

**Q: What is data augmentation and what are the standard techniques for image classification?**
Data augmentation applies random transformations to training images to increase dataset effective size and teach the model invariances. Standard: RandomHorizontalFlip (50% probability), RandomCrop (crop 224x224 from 256x256 image), ColorJitter (brightness/contrast/saturation/hue perturbation). Advanced: Mixup (linear interpolation of two image-label pairs), CutMix (paste random patch from one image into another), RandAugment (randomly sample from 14 operations). Augmentation is applied only to training data, not validation/test data.

**Q: What is the difference between same padding and valid padding?**
Same padding adds P = floor(F/2) zeros around the input so that the output spatial size equals the input size (when stride=1). Valid (no) padding applies convolution only where the filter fully overlaps the input, reducing output size by F-1 per side. Most modern architectures use same padding for 3x3 convs (P=1) to maintain spatial resolution between pooling/striding operations, making output size arithmetic simpler and preventing unintended spatial shrinkage.

**Q: Why are conv layers biased toward local features early and global features late?**
Each neuron in a convolutional layer only connects to a small local region (the receptive field). In early layers, this is just a few pixels. As information flows through stacked layers, each neuron's effective receptive field grows linearly with depth, allowing it to integrate information from increasingly larger spatial regions. This architectural constraint forces the network to build from local primitives (edges, colors) to global abstractions (objects), which matches the compositional structure of visual scenes and is why CNNs generalize so well.

**Q: How do you handle class imbalance in image classification with CNNs?**
Three main strategies: (1) Weighted loss — set per-class weights inversely proportional to class frequency in `nn.CrossEntropyLoss(weight=class_weights)`. (2) Oversampling — use `WeightedRandomSampler` in PyTorch DataLoader to sample rare classes more frequently. (3) Data augmentation — apply heavier augmentation to minority classes. For severe imbalance (>100:1), combine weighted loss with oversampling. Evaluation metric matters: accuracy is misleading for imbalanced datasets; use macro-F1, precision-recall AUC, or per-class recall.

---

## 13. Best Practices

- Always start with transfer learning from ImageNet-pretrained weights unless the domain is radically different (e.g., satellite imagery, medical scans) — even then, try ImageNet init first.
- Apply `cudnn.benchmark = True` for fixed input-size CNN training to get 10-30% speedup via automatic algorithm selection.
- Use `num_workers=4` and `pin_memory=True` in DataLoader for GPU training to overlap data loading with GPU computation.
- Normalize input with dataset-specific or ImageNet statistics (mean, std per channel) — mismatched normalization is a common silent accuracy killer.
- For transfer learning, use differential learning rates: backbone LR = head LR / 10 to avoid catastrophically forgetting pretrained features.
- Monitor validation loss and accuracy per epoch; implement early stopping with patience=10 to prevent wasted compute.
- Use mixed precision (`torch.amp.autocast` + `GradScaler`) — ResNet-50 training saves ~40% memory and runs ~1.7x faster with negligible accuracy impact.
- Do not set `bias=False` in conv layers before BatchNorm — BN subtracts the mean, making the conv bias redundant. Setting `bias=False` is the standard and correct approach (saves parameters, avoids redundant term).
- Preferred augmentation library: `albumentations` for speed (10-50x faster than PIL-based transforms for complex augmentations).

---

## 14. Case Study

**Task**: Fine-tune ResNet-50 for defect detection on a manufacturing line with 8 defect classes + 1 normal class (9 classes total).

**Problem Statement**: 12,000 labeled images (heavily imbalanced: 8,000 normal, 500 per defect class), 224x224 grayscale converted to 3-channel by replication, must classify in real-time at 30 FPS on a workstation GPU. Business requirement: recall >= 0.95 on all defect classes (missed defects are costly).

**Architecture Decisions**:

```
Input: 224x224x3 (grayscale replicated to 3 channels)
Backbone: ResNet-50 pretrained on ImageNet
    Phase 1: freeze all backbone layers, train head only (5 epochs)
    Phase 2: unfreeze layer4, lr_backbone = 1e-5, lr_head = 1e-4 (10 epochs)
    Phase 3: unfreeze layer3+layer4, same LR split (5 epochs)
Head: GlobalAvgPool -> Dropout(0.4) -> Linear(2048, 512) -> ReLU -> Linear(512, 9)
Loss: CrossEntropyLoss with class_weights (inverse frequency)
Augmentation: RandomHorizontalFlip, RandomRotation(10), ColorJitter(0.2, 0.2), RandomCrop(224, padding=16)
```

```python
import torch
import torch.nn as nn
import torchvision.models as models
from torch import Tensor


def build_defect_detector(num_classes: int = 9) -> nn.Module:
    model = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V2)

    # Phase 1: freeze backbone
    for param in model.parameters():
        param.requires_grad = False

    # Replace head
    model.fc = nn.Sequential(
        nn.Dropout(p=0.4),
        nn.Linear(2048, 512),
        nn.ReLU(inplace=True),
        nn.Linear(512, num_classes),
    )
    return model


def get_class_weights(class_counts: list[int], device: torch.device) -> Tensor:
    total = sum(class_counts)
    weights = [total / (len(class_counts) * count) for count in class_counts]
    return torch.tensor(weights, dtype=torch.float32, device=device)


def training_pipeline(model: nn.Module, device: torch.device) -> None:
    class_counts = [8000, 500, 500, 500, 500, 500, 500, 500, 500]
    weights = get_class_weights(class_counts, device)
    criterion = nn.CrossEntropyLoss(weight=weights)

    # Phase 1: head-only
    optimizer = torch.optim.Adam(model.fc.parameters(), lr=1e-3)
    # ... train 5 epochs ...

    # Phase 2: unfreeze layer4
    for param in model.layer4.parameters():
        param.requires_grad = True
    optimizer = torch.optim.Adam([
        {"params": model.layer4.parameters(), "lr": 1e-5},
        {"params": model.fc.parameters(), "lr": 1e-4},
    ])
    # ... train 10 epochs ...
```

**Results**:
- Phase 1 (head only, 5 epochs): validation accuracy 78%, defect recall avg 0.71
- Phase 2 (unfreeze layer4, 10 epochs): validation accuracy 91%, defect recall avg 0.88
- Phase 3 (unfreeze layer3+4, 5 epochs): validation accuracy 94%, defect recall avg 0.96 (meets 0.95 requirement)
- Inference: 2.1ms per image on RTX 3080 (475 FPS >> 30 FPS requirement)
- Training total: 47 minutes

**Key Lessons**:
- Class-weighted loss was essential: without it, the model had 0.12 average defect recall despite 89% overall accuracy (dominated by the 8000 normal samples).
- Greyscale-to-3-channel replication worked well — ImageNet features still transferred meaningfully even though the appearance domain differed.
- Phase 3 (unfreezing layer3) gave the largest per-epoch gain because layer3 features in ImageNet models encode mid-level textures and patterns highly relevant to surface defects.
- Using `cudnn.benchmark = True` reduced per-epoch training time from 4.2 to 3.1 minutes.
