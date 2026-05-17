# Generative Models (GANs, VAEs, Diffusion)

## 1. Concept Overview

Generative models learn the underlying data distribution p(x) and can sample new examples that look like they came from that distribution. Three dominant paradigms exist today: Variational Autoencoders (VAEs), Generative Adversarial Networks (GANs), and Diffusion Models. Each uses a fundamentally different principle to model and sample from complex high-dimensional distributions (images, audio, video, molecules, text).

VAEs use variational inference to learn a structured latent space — they are stable to train and support smooth interpolation between concepts, but tend to produce blurry samples due to the Gaussian assumption. GANs pit a generator against a discriminator in an adversarial game, producing sharp photorealistic samples, but suffer from training instability and mode collapse. Diffusion models iteratively denoise random noise into structured data, achieving state-of-the-art image quality (Stable Diffusion, DALL-E 2, Imagen) at the cost of slower multi-step sampling.

---

## 2. Intuition

VAE analogy: a VAE is a compression algorithm that learns to encode images into a compact code (latent vector) and decode them back, with the constraint that codes must form a nice Gaussian-like cloud in latent space — so you can sample anywhere in that cloud and get a realistic image.

GAN analogy: a GAN is a counterfeiter (generator) vs a detective (discriminator) game. The counterfeiter learns to forge increasingly convincing fake bills; the detective learns to spot them. At equilibrium, the fakes are indistinguishable from real bills.

Diffusion model analogy: a diffusion model is like learning to restore a photo from a sandstorm — you add sand (noise) step by step, then train a network to remove it step by step. At inference, you start with pure sand (Gaussian noise) and denoise for 1000 steps.

Key insight: the field has converged on diffusion models for image generation due to their superior sample quality, stability, and controllability (classifier-free guidance). GANs are still dominant for real-time applications (single forward pass) and video generation. VAEs remain valuable for structured latent space applications (interpolation, editing, downstream tasks).

---

## 3. Core Principles

**VAE ELBO**: the Evidence Lower BOund is the VAE training objective, derived by lower-bounding the log-likelihood log p(x): `ELBO = E[log p(x|z)] - KL(q(z|x) || p(z))`. The first term is reconstruction loss (push decoder to reproduce input); the second is KL divergence (push encoder distribution toward the standard Gaussian prior). Trading off these two terms is the core tension in VAEs.

**Reparameterization trick**: to backpropagate through a sampling operation z ~ N(mu, sigma^2), reparameterize as z = mu + epsilon * sigma where epsilon ~ N(0,1). Gradients flow through mu and sigma (deterministic functions of the encoder), not through the sampling operation (epsilon is treated as a fixed random constant).

**GAN min-max game**: the generator G minimizes and the discriminator D maximizes: `V(G,D) = E[log D(x)] + E[log(1 - D(G(z)))]`. At equilibrium, D(x) = 0.5 everywhere (cannot distinguish real from fake) and G generates samples matching the true data distribution.

**Mode collapse**: the generator learns to produce one or a few modes of the distribution, ignoring the rest. D becomes confused and provides no useful gradient signal. The generator never improves beyond a narrow region.

**Diffusion forward process**: add Gaussian noise over T steps (T=1000 typical) following a variance schedule: `q(x_t | x_{t-1}) = N(x_t; sqrt(1-beta_t)*x_{t-1}, beta_t*I)`. After T steps, x_T is approximately Gaussian noise. The reverse process learns to denoise: `p_theta(x_{t-1}|x_t)`.

**Noise prediction**: DDPM trains a U-Net to predict the noise epsilon added at each step given the noisy image x_t and timestep t: `loss = ||epsilon - epsilon_theta(x_t, t)||^2`. At inference, iteratively apply the learned denoising: start from x_T ~ N(0,I), compute x_0 in T reverse steps.

---

## 4. Types / Architectures / Strategies

**VAE Variants:**

| Variant | Key Modification | Use Case |
|---------|----------------|---------|
| Standard VAE | Gaussian encoder/decoder | Tabular, images |
| beta-VAE | KL weight > 1 (disentanglement) | Disentangled representations |
| VQ-VAE | Discrete latent codes (codebook) | Autoregressive image generation |
| Conditional VAE | Condition latent on label/text | Controlled generation |

**GAN Variants:**

| Variant | Key Innovation | Problem Solved |
|---------|--------------|---------------|
| Vanilla GAN | Original adversarial game | — (mode collapse, instability) |
| DCGAN | Convolutional architecture guidelines | Training stability |
| WGAN | Earth Mover distance, no log | Mode collapse, gradient vanishing |
| WGAN-GP | Gradient penalty (lambda=10) | Lipschitz constraint enforcement |
| StyleGAN2 | Style-based generator, path length reg | High-res face generation, disentanglement |
| BigGAN | Class conditioning, orthogonal reg | Large-scale conditional generation |

**Diffusion Model Variants:**

| Variant | Key Innovation | Sampling Steps |
|---------|--------------|---------------|
| DDPM | Original denoising diffusion | 1000 |
| DDIM | Deterministic sampling | 50 (20x faster) |
| LDM (Latent Diffusion) | Diffuse in VAE latent space (Stable Diffusion) | 50 in latent space |
| Score Matching (SGM) | Score-based continuous SDE | Variable |
| Consistency Models | Single-step distillation from diffusion | 1-2 |

**FID Score (Frechet Inception Distance)**: measures quality and diversity of generated images by comparing statistics of Inception-v3 feature distributions between real and generated sets. Lower FID = better. Computed as: `FID = ||mu_r - mu_g||^2 + Tr(Sigma_r + Sigma_g - 2*(Sigma_r * Sigma_g)^{1/2})`. Requires ~50K generated and real samples for reliable estimates. A FID of 0 would mean identical distributions; DDPM achieves FID ~3.17 on CIFAR-10 (compare: GAN ~4-10).

---

## 5. Architecture Diagrams

```
VAE Architecture:
  x (real image)
      |
  [Encoder: CNN/MLP]
      |
  [mu, log_var]       <- encoder outputs mean and log-variance
      |
  [Reparameterize: z = mu + eps * exp(0.5 * log_var)]
  eps ~ N(0, I)       <- random noise, fixed during backward pass
      |
  [Decoder: CNN/MLP]
      |
  x_hat (reconstruction)
      |
  Loss = Recon(x, x_hat) + beta * KL(N(mu, sigma) || N(0, I))

Latent Space:
  [concept A] --- interpolation path --- [concept B]
  z_interp = t * z_a + (1-t) * z_b, t in [0,1]
  Because latent space is continuous Gaussian: smooth interpolation works

GAN Architecture:
  z ~ N(0,I) -> [Generator G] -> x_fake
                                      |
  x_real ------> [Discriminator D] <--+
                      |
              D(x_real) -> 1 (real)
              D(x_fake) -> 0 (fake)

  Generator loss:    L_G = -E[log D(G(z))]        (minimize chance D detects fake)
  Discriminator loss: L_D = -E[log D(x)] - E[log(1 - D(G(z)))]  (maximize detection)

Diffusion (DDPM) Forward + Reverse:
Forward (add noise, fixed, no parameters):
  x_0 (real) -> x_1 (slight noise) -> x_2 -> ... -> x_T (pure noise)
  q(x_t | x_0) = N(x_t; sqrt(alpha_bar_t) * x_0, (1 - alpha_bar_t) * I)

Reverse (learned denoising):
  x_T (noise) -> [U-Net + t embedding] -> predicted_noise
              -> x_{T-1} = denoise(x_T, predicted_noise)
              -> ... -> x_0 (generated image)

U-Net for Diffusion:
  Encoder path: [Conv] -> [ResBlock + Attn] -> [Downsample] -> ... (extract features)
  Bottleneck:   [ResBlock + Attn] (global context)
  Decoder path: [Upsample] -> [ResBlock + Attn + skip from encoder] -> ...
  Conditioning: timestep t is embedded and added to each ResBlock
                text prompt conditioning via cross-attention (CLIP encoder)

Classifier-Free Guidance (CFG):
  epsilon_guided = epsilon_uncond + scale * (epsilon_cond - epsilon_uncond)
  scale=1: no guidance (creative, diverse)
  scale=7.5: standard (quality/diversity balance)
  scale=15+: high fidelity to prompt (less diverse, possible artifacts)
```

---

## 6. How It Works — Detailed Mechanics

### VAE Implementation

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch import Tensor


class VAEEncoder(nn.Module):
    def __init__(self, input_dim: int, hidden_dim: int, latent_dim: int) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, hidden_dim),
            nn.ReLU(),
        )
        self.mu_layer      = nn.Linear(hidden_dim, latent_dim)
        self.log_var_layer = nn.Linear(hidden_dim, latent_dim)

    def forward(self, x: Tensor) -> tuple[Tensor, Tensor]:
        h = self.net(x)
        mu = self.mu_layer(h)
        log_var = self.log_var_layer(h)  # log(sigma^2) — unconstrained
        return mu, log_var


class VAEDecoder(nn.Module):
    def __init__(self, latent_dim: int, hidden_dim: int, output_dim: int) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(latent_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, output_dim),
            nn.Sigmoid(),  # pixel values in [0,1]
        )

    def forward(self, z: Tensor) -> Tensor:
        return self.net(z)


class VAE(nn.Module):
    def __init__(
        self, input_dim: int = 784, hidden_dim: int = 512, latent_dim: int = 32, beta: float = 1.0
    ) -> None:
        super().__init__()
        self.encoder = VAEEncoder(input_dim, hidden_dim, latent_dim)
        self.decoder = VAEDecoder(latent_dim, hidden_dim, input_dim)
        self.beta = beta  # beta > 1 encourages disentanglement (beta-VAE)

    def reparameterize(self, mu: Tensor, log_var: Tensor) -> Tensor:
        """
        Reparameterization trick: z = mu + eps * sigma
        eps ~ N(0, I) — fixed random constant during backward
        Gradients flow through mu and sigma (not through sampling)
        """
        if self.training:
            std = torch.exp(0.5 * log_var)  # sigma = exp(log_var / 2)
            eps = torch.randn_like(std)     # eps ~ N(0, I), same shape as std
            return mu + eps * std
        else:
            return mu  # at inference, use mean directly (deterministic)

    def forward(self, x: Tensor) -> tuple[Tensor, Tensor, Tensor]:
        mu, log_var = self.encoder(x)
        z = self.reparameterize(mu, log_var)
        x_hat = self.decoder(z)
        return x_hat, mu, log_var

    def loss(self, x: Tensor, x_hat: Tensor, mu: Tensor, log_var: Tensor) -> Tensor:
        # Reconstruction loss: binary cross-entropy for image pixels in [0,1]
        recon_loss = F.binary_cross_entropy(x_hat, x, reduction="sum")
        # KL divergence: KL(N(mu, sigma) || N(0, 1))
        # Closed form: -0.5 * sum(1 + log_var - mu^2 - exp(log_var))
        kl_loss = -0.5 * torch.sum(1 + log_var - mu.pow(2) - log_var.exp())
        return (recon_loss + self.beta * kl_loss) / x.size(0)  # per-sample average

    @torch.no_grad()
    def sample(self, n: int, device: torch.device) -> Tensor:
        z = torch.randn(n, self.encoder.mu_layer.out_features, device=device)
        return self.decoder(z)

    @torch.no_grad()
    def interpolate(self, x1: Tensor, x2: Tensor, steps: int = 10) -> Tensor:
        mu1, _ = self.encoder(x1)
        mu2, _ = self.encoder(x2)
        alphas = torch.linspace(0, 1, steps, device=x1.device)
        # Linear interpolation in latent space
        zs = torch.stack([(1-a)*mu1 + a*mu2 for a in alphas])
        return self.decoder(zs)
```

### WGAN with Gradient Penalty

```python
class WGANGPTrainer:
    def __init__(
        self,
        generator: nn.Module,
        critic: nn.Module,     # discriminator is called critic in WGAN
        device: torch.device,
        lr: float = 1e-4,
        gp_lambda: float = 10.0,     # gradient penalty coefficient
        n_critic: int = 5,           # critic steps per generator step
    ) -> None:
        self.G = generator
        self.D = critic               # critic: outputs real number, not probability
        self.device = device
        self.gp_lambda = gp_lambda
        self.n_critic = n_critic
        # Note: no beta2 tuning needed — WGAN uses RMSprop or Adam with low beta values
        self.opt_G = torch.optim.Adam(generator.parameters(), lr=lr, betas=(0.0, 0.9))
        self.opt_D = torch.optim.Adam(critic.parameters(),    lr=lr, betas=(0.0, 0.9))

    def gradient_penalty(self, real: Tensor, fake: Tensor) -> Tensor:
        """
        WGAN-GP: enforce Lipschitz constraint via gradient penalty on interpolated samples.
        Penalizes critic when gradient norm != 1 at points between real and fake distributions.
        """
        batch_size = real.size(0)
        alpha = torch.rand(batch_size, 1, 1, 1, device=self.device)  # random interpolation
        interpolated = (alpha * real + (1 - alpha) * fake).requires_grad_(True)
        d_interpolated = self.D(interpolated)
        gradients = torch.autograd.grad(
            outputs=d_interpolated,
            inputs=interpolated,
            grad_outputs=torch.ones_like(d_interpolated),
            create_graph=True,
            retain_graph=True,
        )[0]
        gradients = gradients.view(batch_size, -1)
        gradient_norm = gradients.norm(2, dim=1)
        # Penalty: push gradient norm toward 1
        return ((gradient_norm - 1) ** 2).mean()

    def train_step(self, real_batch: Tensor, latent_dim: int) -> dict[str, float]:
        real = real_batch.to(self.device)
        batch_size = real.size(0)
        metrics: dict[str, float] = {}

        # Train critic n_critic times per generator step
        for _ in range(self.n_critic):
            z = torch.randn(batch_size, latent_dim, device=self.device)
            fake = self.G(z).detach()  # detach: do not backprop into G during D update

            # Wasserstein critic loss: maximize E[D(real)] - E[D(fake)]
            # (equivalently minimize negative)
            d_real = self.D(real).mean()
            d_fake = self.D(fake).mean()
            gp = self.gradient_penalty(real, fake)
            loss_D = -d_real + d_fake + self.gp_lambda * gp

            self.opt_D.zero_grad()
            loss_D.backward()
            self.opt_D.step()

        # Train generator: maximize E[D(G(z))] = minimize -E[D(G(z))]
        z = torch.randn(batch_size, latent_dim, device=self.device)
        fake = self.G(z)
        loss_G = -self.D(fake).mean()

        self.opt_G.zero_grad()
        loss_G.backward()
        self.opt_G.step()

        metrics["loss_G"] = loss_G.item()
        metrics["loss_D"] = loss_D.item()
        metrics["W_dist"] = (d_real - d_fake).item()  # Wasserstein distance estimate
        return metrics
```

### Diffusion Model (DDPM)

```python
import numpy as np


class DDPMNoiseScheduler:
    def __init__(
        self,
        num_timesteps: int = 1000,
        beta_start: float = 1e-4,
        beta_end: float = 0.02,
    ) -> None:
        self.T = num_timesteps
        # Linear noise schedule (DDPM original; cosine schedule is better for modern models)
        self.betas = torch.linspace(beta_start, beta_end, num_timesteps)
        self.alphas = 1.0 - self.betas
        self.alpha_bars = torch.cumprod(self.alphas, dim=0)  # alpha_bar_t = prod_{s=1}^{t} alpha_s

    def add_noise(self, x_0: Tensor, t: Tensor) -> tuple[Tensor, Tensor]:
        """
        Forward process: sample x_t given x_0 in one step (closed form).
        x_t = sqrt(alpha_bar_t) * x_0 + sqrt(1 - alpha_bar_t) * eps
        eps ~ N(0, I)
        """
        alpha_bar = self.alpha_bars[t].view(-1, 1, 1, 1).to(x_0.device)
        eps = torch.randn_like(x_0)
        x_t = torch.sqrt(alpha_bar) * x_0 + torch.sqrt(1 - alpha_bar) * eps
        return x_t, eps  # return both noisy image and the noise (target for U-Net)

    @torch.no_grad()
    def sample_one_step(
        self,
        model: nn.Module,
        x_t: Tensor,
        t: int,
    ) -> Tensor:
        """Single reverse denoising step."""
        beta_t = self.betas[t].to(x_t.device)
        alpha_t = self.alphas[t].to(x_t.device)
        alpha_bar_t = self.alpha_bars[t].to(x_t.device)

        t_batch = torch.full((x_t.size(0),), t, device=x_t.device, dtype=torch.long)
        eps_pred = model(x_t, t_batch)  # U-Net predicts the noise

        # DDPM reverse step formula
        x_prev = (x_t - (1 - alpha_t) / torch.sqrt(1 - alpha_bar_t) * eps_pred)
        x_prev = x_prev / torch.sqrt(alpha_t)

        if t > 0:  # add noise at all steps except the last
            noise = torch.randn_like(x_t)
            x_prev = x_prev + torch.sqrt(beta_t) * noise

        return x_prev

    @torch.no_grad()
    def generate(self, model: nn.Module, shape: tuple, device: torch.device) -> Tensor:
        """Full reverse diffusion: T denoising steps from pure noise."""
        model.eval()
        x = torch.randn(shape, device=device)  # start from pure Gaussian noise
        for t in reversed(range(self.T)):       # T=999 down to 0
            x = self.sample_one_step(model, x, t)
        return x.clamp(-1, 1)


def ddpm_training_step(
    model: nn.Module,
    scheduler: DDPMNoiseScheduler,
    x_0: Tensor,
    optimizer: torch.optim.Optimizer,
    device: torch.device,
) -> float:
    model.train()
    x_0 = x_0.to(device)
    batch_size = x_0.size(0)

    # Sample random timesteps for this batch (different t for each sample)
    t = torch.randint(0, scheduler.T, (batch_size,), device=device, dtype=torch.long)

    # Add noise to get x_t
    x_t, eps_true = scheduler.add_noise(x_0, t)

    # Predict the noise
    optimizer.zero_grad()
    eps_pred = model(x_t, t)

    # Simple MSE loss on noise (DDPM original formulation)
    loss = F.mse_loss(eps_pred, eps_true)
    loss.backward()
    nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
    optimizer.step()
    return loss.item()
```

### Classifier-Free Guidance

```python
@torch.no_grad()
def cfg_sample(
    model: nn.Module,
    scheduler: DDPMNoiseScheduler,
    text_embedding: Tensor,        # (batch, seq, dim) from CLIP/T5
    shape: tuple,
    cfg_scale: float = 7.5,        # guidance scale: 1=no guidance, 7.5=standard, 15+=strong
    device: torch.device = torch.device("cuda"),
) -> Tensor:
    """
    Classifier-Free Guidance sampling.
    At each step, predict noise twice: with conditioning and with null conditioning.
    Extrapolate in the direction of the condition.
    """
    model.eval()
    null_embedding = torch.zeros_like(text_embedding)  # unconditional: empty/null prompt
    x = torch.randn(shape, device=device)

    for t in reversed(range(scheduler.T)):
        t_batch = torch.full((shape[0],), t, device=device, dtype=torch.long)

        # Predict noise conditioned on text
        eps_cond   = model(x, t_batch, text_embedding)
        # Predict noise unconditionally
        eps_uncond = model(x, t_batch, null_embedding)

        # CFG: extrapolate from unconditional toward conditional
        eps_guided = eps_uncond + cfg_scale * (eps_cond - eps_uncond)

        x = scheduler.sample_one_step_with_eps(x, t, eps_guided)

    return x.clamp(-1, 1)
```

### Mode Collapse Detection

```python
def detect_mode_collapse(
    generator: nn.Module,
    n_samples: int = 1000,
    latent_dim: int = 128,
    device: torch.device = torch.device("cuda"),
) -> dict[str, float]:
    """
    Heuristic mode collapse detection:
    - Low diversity: generated samples cluster together (low pairwise distance)
    - Low Inception Score (IS): generated samples lack diversity
    """
    generator.eval()
    with torch.no_grad():
        z = torch.randn(n_samples, latent_dim, device=device)
        fake = generator(z).view(n_samples, -1).cpu()

    # Pairwise cosine similarity — high mean = mode collapse
    fake_normalized = F.normalize(fake, dim=1)
    # Sample 500 pairs to estimate pairwise similarity efficiently
    idx_a = torch.randint(0, n_samples, (500,))
    idx_b = torch.randint(0, n_samples, (500,))
    pairwise_sim = (fake_normalized[idx_a] * fake_normalized[idx_b]).sum(dim=1)
    mean_sim = pairwise_sim.mean().item()

    return {
        "mean_pairwise_similarity": mean_sim,
        "mode_collapsed": mean_sim > 0.95,  # threshold: >0.95 suggests collapse
    }
```

---

## 7. Real-World Examples

**Stable Diffusion (Latent Diffusion Model)**: compresses images to 64x64 latent space via a VQ-VAE (8x compression from 512x512), then runs DDPM in that latent space. This reduces diffusion compute by 64x vs pixel-space diffusion. Uses a CLIP text encoder for conditioning and classifier-free guidance at scale 7.5. Generates 512x512 images in ~2 seconds on a consumer RTX 3090 (50 DDIM steps).

**StyleGAN2 for face generation**: achieves FID ~2.84 on FFHQ (70K face dataset). Uses progressive growing, style injection, and path length regularization to avoid mode collapse and achieve photorealistic diversity. Deployed by NVIDIA for avatar creation and data augmentation in medical imaging (synthetic face data for privacy-preserving datasets).

**VQ-VAE-2 for hierarchical image generation**: combines a discrete latent codebook (VQ-VAE) with an autoregressive prior (PixelCNN). Achieves diverse high-resolution generation without adversarial training or diffusion. The codebook approach enables discrete manipulation of image structure (changing hairstyle without changing identity).

**VAE for drug discovery (Molecular VAE)**: encodes molecule SMILES strings into a continuous latent space, enabling gradient-based optimization of molecular properties. Novartis and Insilico Medicine use this to explore chemical space efficiently — latent space interpolation between two molecules produces valid intermediate compounds in ~80% of cases vs ~5% for random exploration.

---

## 8. Tradeoffs

| Model | Training Stability | Sample Quality | Diversity | Inference Speed | Latent Control |
|-------|-----------------|--------------|---------|---------------|---------------|
| VAE | Excellent | Blurry | Good | Fast (1 step) | Excellent |
| GAN | Poor (collapse risk) | Sharp/Realistic | Risk of collapse | Fast (1 step) | Limited |
| WGAN-GP | Good | Better than GAN | Good | Fast | Limited |
| DDPM | Excellent | SOTA | Excellent | Slow (1000 steps) | Moderate |
| DDIM | Excellent | SOTA | Excellent | Medium (50 steps) | Moderate |
| LDM | Excellent | SOTA | Excellent | Medium (50 latent) | Good with CFG |

| FID Score Comparison (CIFAR-10 32x32) | FID |
|---------------------------------------|-----|
| Real data | 0 |
| DDPM | ~3.2 |
| StyleGAN2 | ~4.4 |
| NVAE (hierarchical VAE) | ~23.5 |
| Standard VAE | ~60-100 |

---

## 9. When to Use / When NOT to Use

**Use VAE when:**
- You need a smooth, interpretable latent space (interpolation, attribute manipulation)
- Training stability is paramount (no adversarial dynamics)
- Downstream tasks use the latent representations (clustering, drug discovery)
- Sample quality is secondary to latent space structure

**Use GAN/StyleGAN when:**
- You need the sharpest possible samples in a single forward pass
- High-resolution portrait, texture, or domain-specific generation (faces, art)
- Real-time inference with <10ms latency requirement

**Use Diffusion (DDPM/LDM) when:**
- Maximum sample quality and diversity are required
- Text-conditional generation (DALL-E, Stable Diffusion, Imagen)
- Inference latency allows multi-step sampling (>0.5 seconds acceptable)
- Training stability is more important than training speed

**Do NOT use:**
- Vanilla GAN for complex diverse datasets (use WGAN-GP or StyleGAN2)
- Standard VAE when sample quality matters (blurry output is a hard limitation)
- DDPM (1000 steps) when fast inference is needed — use DDIM (50 steps) instead

---

## 10. Common Pitfalls

**War story 1 — GAN mode collapse due to imbalanced training:**
A GAN trained on a fashion dataset collapsed to generating only white t-shirts within 500 steps. Investigation: the discriminator was updated once per generator step. The discriminator converged quickly (~50 steps) and provided zero-gradient signal to the generator for all non-t-shirt images (D(G(z)) ≈ 0 everywhere). Fix: use WGAN-GP (gradient penalty forces gradients to remain informative) and train the discriminator/critic 5 steps per generator step (n_critic=5). Mode diversity measured by pairwise cosine similarity dropped from 0.97 (collapse) to 0.34 (healthy).

```python
# BROKEN: equal training — discriminator dominates
for x_real in loader:
    update_D(x_real)  # D converges first, gradients become 0
    update_G()        # G receives no useful signal

# FIX: WGAN-GP with n_critic=5
for x_real in loader:
    for _ in range(5):       # train critic 5x more
        update_critic_wgan_gp(x_real)
    update_generator()
```

**War story 2 — VAE posterior collapse (KL vanishing):**
A conditional VAE for text generation had KL loss go to zero after epoch 3 — the encoder learned to output mu=0, sigma=1 (matching the prior exactly), and the decoder learned to ignore the latent variable z entirely. The model became a standard autoregressive decoder with no latent structure. Fix: KL annealing — linearly increase the KL coefficient (beta) from 0 to 1 over the first 10 epochs, allowing the reconstruction loss to dominate early training and forcing the latent space to encode useful information before the KL penalty kicks in.

```python
# FIX: KL annealing — gradually increase KL weight
def get_kl_weight(epoch: int, warmup_epochs: int = 10) -> float:
    return min(1.0, epoch / warmup_epochs)

for epoch in range(max_epochs):
    kl_weight = get_kl_weight(epoch, warmup_epochs=10)
    for x in loader:
        x_hat, mu, log_var = vae(x)
        recon = F.binary_cross_entropy(x_hat, x, reduction="sum")
        kl = -0.5 * torch.sum(1 + log_var - mu.pow(2) - log_var.exp())
        loss = recon + kl_weight * kl  # kl_weight starts at 0
        # ... backward, step
```

**War story 3 — Forgetting to normalize images to [-1, 1] for GAN:**
A DCGAN implementation trained on images in [0, 255] (not normalized). The generator's tanh output layer produces values in [-1, 1]. The discriminator received real images in [0, 255] and fake images in [-1, 1] — completely different distributions. D learned to distinguish based on value range alone (trivial task), providing no useful gradient to G. G never improved. Fix: always normalize input images to [-1, 1] for GANs, matching the tanh output range of the generator.

```python
# BROKEN: raw pixel values
transform = transforms.ToTensor()  # scales to [0, 1] — still wrong for GAN

# FIX: normalize to [-1, 1] to match tanh generator output
transform = transforms.Compose([
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.5, 0.5, 0.5], std=[0.5, 0.5, 0.5]),  # -> [-1, 1]
])
```

**War story 4 — Diffusion model trained without timestep conditioning:**
A diffusion model U-Net was implemented but the timestep t embedding was silently dropped (a bug in the conditioning code path). The model received the same noisy image x_t but had no way to know how much noise was added. It learned an average denoising behavior that worked poorly for both lightly and heavily noised images. FID was 47 vs expected ~4. Fix: always verify the timestep embedding is actually being added to the U-Net (check intermediate activation statistics across different t values — they should differ significantly).

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `diffusers` (HuggingFace) | DDPM, DDIM, Stable Diffusion, ControlNet training and inference |
| `torchvision.utils.make_grid` | Visualize generated image grids |
| `pytorch-fid` | Compute FID score for generated images |
| `clean-fid` | More accurate FID (proper resize filters) |
| `StyleGAN2-ADA` (NVIDIA) | Reference StyleGAN2 implementation with limited data augmentation |
| `accelerate` (HuggingFace) | Multi-GPU/TPU training for diffusion models |
| CLIP (`openai/clip`) | Text encoder for conditional generation |
| `einops` | Tensor reshaping for attention in U-Net |
| `xformers` | Memory-efficient attention for diffusion U-Net (reduces VRAM 40-60%) |

Key `diffusers` APIs:
```python
from diffusers import DDPMScheduler, UNet2DModel, DDIMScheduler

# Load pretrained diffusion model
scheduler = DDPMScheduler(num_train_timesteps=1000)
unet = UNet2DModel.from_pretrained("google/ddpm-celebahq-256")

# DDIM for 50-step sampling (20x faster than DDPM 1000 steps)
ddim_scheduler = DDIMScheduler.from_pretrained("google/ddpm-celebahq-256")
ddim_scheduler.set_timesteps(50)
```

---

## 12. Interview Questions with Answers

**Q: What is the reparameterization trick and why is it needed in VAEs?**
The reparameterization trick enables backpropagation through a sampling operation. In a VAE, the latent variable z is sampled from N(mu, sigma^2), which is non-differentiable — you cannot compute gradients with respect to mu and sigma through a sampling operation. The trick rewrites sampling as z = mu + epsilon * sigma where epsilon ~ N(0, I). Now mu and sigma are deterministic functions of the encoder output (and are differentiable), while epsilon is a fixed random constant that does not require gradients. This allows the gradient of the ELBO loss to flow through z to the encoder parameters.

**Q: What is the ELBO and what are its two terms?**
ELBO (Evidence Lower BOund) is the VAE training objective, a lower bound on log p(x). It equals E_q[log p(x|z)] - KL(q(z|x) || p(z)). The first term is the reconstruction loss: given a latent code z sampled from the encoder, how well can the decoder reconstruct the original input x? For images, this is often binary cross-entropy or MSE. The second term is the KL divergence: how far is the encoder's distribution q(z|x) from the prior p(z) = N(0,I)? Minimizing negative ELBO means maximizing reconstruction quality while keeping the latent space close to a standard Gaussian — enabling sampling at inference by drawing z from N(0,I).

**Q: What is mode collapse in GANs and how does WGAN-GP address it?**
Mode collapse occurs when the generator learns to produce samples from a narrow subset of the true data distribution (e.g., only one digit class, one face expression) because it found a small region that fools the discriminator. The discriminator then learns to distinguish this narrow region, the generator shifts to another narrow region, and this cycling continues without convergence. WGAN addresses this by using Earth Mover (Wasserstein) distance as the objective, which provides gradient signal everywhere (not just near the decision boundary). WGAN-GP enforces the Lipschitz constraint required by WGAN via a gradient penalty: at random interpolations between real and fake samples, the critic's gradient norm should be 1. This prevents the critic from saturating and ensures informative gradients to the generator throughout training.

**Q: Describe the forward and reverse processes in DDPM.**
Forward process: starting from a real image x_0, add Gaussian noise over T=1000 steps using a fixed variance schedule. At each step t: x_t = sqrt(1-beta_t) * x_{t-1} + sqrt(beta_t) * eps. After T steps, x_T is approximately N(0,I). Crucially, x_t can be sampled directly from x_0 in one step: x_t = sqrt(alpha_bar_t) * x_0 + sqrt(1-alpha_bar_t) * eps, enabling efficient training without sequential forward steps. Reverse process: a U-Net is trained to predict the noise eps given (x_t, t). At inference, start from x_T ~ N(0,I) and iteratively denoise using the learned U-Net, recovering x_0 over T reverse steps.

**Q: What is classifier-free guidance and how does it control generation quality?**
Classifier-free guidance (CFG) trains a single diffusion model to handle both conditional (with a text prompt) and unconditional (empty prompt) generation by randomly dropping the conditioning 10-20% of training time. At inference, two noise predictions are made per step — conditional and unconditional — and the guided prediction extrapolates in the direction of the condition: eps_guided = eps_uncond + scale * (eps_cond - eps_uncond). The guidance scale controls the tradeoff: scale=1 gives no guidance (maximum diversity); scale=7.5 is standard (quality/diversity balance used by Stable Diffusion); scale=15+ produces images highly faithful to the prompt but less diverse and sometimes over-saturated. This requires no separate classifier, making it simpler and more flexible than classifier guidance.

**Q: What is FID and how do you interpret it?**
Frechet Inception Distance measures the similarity between the distribution of real images and generated images in the feature space of a pretrained Inception-v3 network. It computes the Frechet distance between two multivariate Gaussians fitted to the Inception features of real and generated samples: FID = ||mu_r - mu_g||^2 + Tr(Sigma_r + Sigma_g - 2*sqrt(Sigma_r * Sigma_g)). Lower FID is better: a perfect generator would achieve FID=0. DDPM achieves FID~3.2 on CIFAR-10; standard VAE achieves FID~60-100. Requires ~50K samples for statistical reliability — computing FID on fewer samples (e.g., 2K) produces unreliable estimates that vary by +-5-10 FID points.

**Q: What is the difference between DDPM and DDIM sampling?**
DDPM (Denoising Diffusion Probabilistic Models) uses a stochastic reverse process that requires T=1000 sequential denoising steps to generate a high-quality sample. Each step depends on the previous and adds random noise, making it inherently slow. DDIM (Denoising Diffusion Implicit Models) reformulates the reverse process as a deterministic ODE solver, enabling sampling with far fewer steps (typically 50) with minimal quality degradation (~same FID). DDIM is also deterministic given the initial noise x_T, enabling reproducible generation. Stable Diffusion uses DDIM (or PNDM/DPM++) by default. The tradeoff: DDIM at 20 steps produces slightly lower quality than DDPM at 1000 steps; DDIM at 50 steps is nearly equivalent.

**Q: What causes posterior collapse in VAEs and how do you fix it?**
Posterior collapse occurs when the encoder learns to output the prior (mu=0, sigma=1) and the decoder learns to generate good reconstructions by ignoring z entirely. This happens because in early training, a powerful decoder can minimize reconstruction loss without using z, while the KL term pushes toward the prior. Once the decoder learns to ignore z, the encoder has no incentive to encode useful information. Fixes: (1) KL annealing — linearly increase KL weight from 0 to 1 over early training epochs, forcing reconstruction to dominate first; (2) Free bits — set a minimum KL per dimension (e.g., 0.5 nats) below which KL is not penalized; (3) Use a weaker decoder architecture that forces reliance on z; (4) Reduce decoder capacity for text VAEs (LSTM decoder).

**Q: What is the difference between a VAE and a VQ-VAE?**
A standard VAE uses a continuous Gaussian latent space. A VQ-VAE (Vector Quantized VAE) uses a discrete latent space: the encoder maps inputs to a sequence of discrete codes from a learned codebook (typically 512-8192 codes). The closest codebook vector to each encoder output is used (straight-through estimator for gradient). VQ-VAE avoids posterior collapse (discrete codes cannot collapse to a prior), produces sharper reconstructions than Gaussian VAEs, and enables powerful autoregressive priors (PixelCNN, Transformer) over the discrete codes. Stable Diffusion uses a VQ-VAE variant to compress images to a latent space before diffusion. The limitation is that training is more complex (codebook collapse — unused codes — requires commitment loss and exponential moving average updates).

**Q: How would you diagnose and fix training instability in a GAN?**
Symptoms: oscillating loss, mode collapse (D detects fakes trivially), loss of one network going to zero. Diagnosis steps: (1) Plot discriminator accuracy on real and fake — should be ~50-75% during healthy training; >95% means G is failing, <55% means D is too weak. (2) Check gradient norms for both networks. (3) Visualize generated samples every N steps to catch collapse early. Fixes: (1) Reduce discriminator LR or increase generator LR; (2) Switch to WGAN-GP for stable Lipschitz enforcement; (3) Add feature matching loss (generator minimizes difference in discriminator intermediate features, not just final output); (4) Use mini-batch discrimination to prevent mode collapse; (5) Add label smoothing to discriminator targets (real label = 0.9, not 1.0).

**Q: What is latent space interpolation in VAEs and why does it work smoothly?**
Latent space interpolation creates a sequence of images by linearly interpolating between two latent codes: z_interp = (1-t) * z_a + t * z_b for t in [0,1]. This produces smooth intermediate images that look like valid samples from the data distribution. It works because the VAE's KL regularization forces the latent space to be a well-structured Gaussian — every point in the space corresponds to a valid image, and nearby points correspond to similar images. This is in contrast to a standard autoencoder where the latent space has "holes" (points between encodings may decode to nonsense). Applications: face aging by interpolating along the age dimension, style transfer by mixing latent codes from two images.

---

## 13. Best Practices

- Use WGAN-GP over vanilla GAN for any non-trivial dataset. The gradient penalty adds minimal overhead and dramatically improves stability.
- Monitor diversity alongside quality: track both FID (quality + diversity) and precision/recall separately. High precision, low recall = mode collapse.
- For VAEs, use KL annealing (beta from 0 to 1 over 10 epochs) to prevent posterior collapse, especially with powerful decoders.
- Normalize GAN input images to [-1, 1] to match the tanh generator output range. This is one of the most common silent correctness bugs.
- Use the diffusers library for all diffusion model work — the scheduler implementations are numerically verified and include DDIM, DPM++, and other fast samplers.
- For text-to-image generation, prefer Latent Diffusion (run diffusion in VAE latent space) over pixel-space diffusion — 8-64x compute reduction at equivalent quality.
- Set CFG scale 7.5 as the default starting point for Stable Diffusion; adjust based on prompt adherence vs diversity requirements.
- Always compute FID with at least 10K generated samples (50K for publishable results). FID computed on 1K samples has variance of +-10-15 FID points.
- For GAN training: update the discriminator 5x per generator step (n_critic=5 in WGAN-GP). Use Adam with betas=(0.0, 0.9) — not (0.9, 0.999) — for both networks to prevent momentum from destabilizing adversarial dynamics.
- Use `xformers` memory-efficient attention in diffusion U-Nets to reduce VRAM by 40-60%, enabling larger batch sizes or higher resolutions.

---

## 14. Case Study

**Task**: Build a product image generation system for an e-commerce platform to augment a sparse catalog. The platform has 50K product images across 200 categories, many categories with < 100 images. Goal: generate diverse, high-quality product images for data augmentation and catalog preview.

**Problem Statement**: Training downstream classifiers with 50K real images yields 82% accuracy. Adding 200K synthetic images should improve to 90%+. Synthetic images must be: (1) photorealistic (FID < 20 vs real products), (2) category-consistent (a generated "shoe" looks like a shoe), (3) diverse (no mode collapse).

**Architecture Decision: Conditional LDM (Latent Diffusion Model)**

Rationale for LDM over GAN: training stability (no mode collapse risk), better diversity, text-conditional control via CLIP. Against pixel-space diffusion: compute efficiency (8x compression).

```
Pipeline:
  1. VQ-VAE: compress 256x256 product images to 32x32x4 latent
  2. CLIP: encode category names to text embeddings
  3. LDM U-Net: diffuse in 32x32x4 latent space, conditioned on CLIP embeddings
  4. VQ-VAE decoder: decode 32x32x4 latent to 256x256 RGB image

Training:
  - VQ-VAE: train on all 50K products first (reconstruction + codebook loss)
    Reconstruction loss: L1 + perceptual (VGG) + patch discriminator (adversarial)
  - LDM: train U-Net in latent space with CFG (10% unconditional drops)
  - Optimizer: AdamW, lr=1e-4, weight_decay=1e-2
  - LR schedule: constant (LDM is less sensitive to schedule than discriminative models)
  - Mixed precision: fp16 + GradScaler
  - Batch: 64 per GPU, 4 GPUs, gradient_accumulation=2 -> effective batch=512
  - Epochs: 300 for VQ-VAE (fast), 500 for LDM
```

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch import Tensor
from diffusers import UNet2DConditionModel, DDIMScheduler


class ProductImageGenerator:
    def __init__(
        self,
        vqvae: nn.Module,
        unet: UNet2DConditionModel,
        clip_encoder: nn.Module,
        device: torch.device,
    ) -> None:
        self.vqvae = vqvae
        self.unet = unet
        self.clip = clip_encoder
        self.scheduler = DDIMScheduler(num_train_timesteps=1000)
        self.scheduler.set_timesteps(50)   # 50 DDIM steps at inference
        self.device = device

    @torch.no_grad()
    def generate(
        self,
        category: str,
        n_images: int = 4,
        cfg_scale: float = 7.5,
    ) -> Tensor:
        """Generate product images for a given category."""
        self.unet.eval()
        self.vqvae.eval()

        # Encode category text with CLIP
        text_emb = self.clip.encode_text([category] * n_images)           # (n, 77, 768)
        null_emb = self.clip.encode_text([""] * n_images)                 # unconditional

        # Start from noise in latent space (32x32x4 for 256x256 images with 8x compression)
        latent = torch.randn(n_images, 4, 32, 32, device=self.device)

        for t in self.scheduler.timesteps:
            # Predict noise with and without conditioning (CFG)
            eps_cond   = self.unet(latent, t, encoder_hidden_states=text_emb).sample
            eps_uncond = self.unet(latent, t, encoder_hidden_states=null_emb).sample
            eps_guided = eps_uncond + cfg_scale * (eps_cond - eps_uncond)

            latent = self.scheduler.step(eps_guided, t, latent).prev_sample

        # Decode latent to pixel space
        images = self.vqvae.decode(latent)          # (n, 3, 256, 256), values in [-1, 1]
        images = (images + 1) / 2                   # rescale to [0, 1]
        return images.clamp(0, 1)
```

**Results**:
- VQ-VAE reconstruction: PSNR=28.4 dB, SSIM=0.87 — high-quality lossless compression
- LDM FID on held-out product images: 14.3 (vs target < 20) — photorealistic
- Category consistency: 91% of generated images correctly classified by a pretrained ResNet-50 into the intended category
- Diversity: mean pairwise cosine similarity 0.42 (vs 0.97 for mode-collapsed GAN baseline tested earlier)
- Downstream classifier impact: adding 200K synthetic images improved product classification from 82% to 91% val accuracy (target 90% achieved)
- Generation throughput: 4 images per second on A100 (50 DDIM steps, 256x256)

**Key Lessons**:
- CFG scale required per-category tuning: apparel worked best at 6.0 (needs diversity — many styles), electronics at 9.0 (needs prompt adherence — specific product details matter). A fixed scale of 7.5 was suboptimal.
- VQ-VAE training with a patch discriminator (adversarial component) was critical — without it, decoded images had soft, blurry textures despite good PSNR. The adversarial loss adds perceptual sharpness.
- Generating images for rare categories (< 50 training images) produced lower quality (FID 28 vs 12 for common categories). Fine-tuning the LDM for 100 steps on rare category images improved FID to 17 — full fine-tuning was not needed.
- FID computed on only 1K generated images was unreliable (variance +-8 FID) vs 10K images (variance +-1.5). Initial FID evaluation with 1K samples led to premature conclusions about model quality.
