# Adversarial Machine Learning and Robustness

> Phase 7 (Advanced Topics). This module covers attacks against ML models (evasion,
> poisoning, extraction, inference) and the defenses that harden them. The LLM analog —
> prompt injection, jailbreaks, and content safety — lives in `llm/llm_security/` and
> `llm/guardrails_and_content_safety/`; the threat-model vocabulary here transfers directly.

---

## 1. Concept Overview

Standard ML assumes the data at inference time is drawn from the same distribution as training data and that no one is trying to manipulate the model. Adversarial ML drops that assumption. It studies how an intelligent attacker can degrade, mislead, steal, or extract information from a model — and how to defend against it.

There are four canonical attack surfaces:

1. **Evasion (inference-time):** craft an input that is correctly handled by humans but misclassified by the model — a few imperceptibly modified pixels turn a "panda" into a "gibbon," or a spam email slips past a filter.
2. **Poisoning (training-time):** corrupt the training data so the deployed model learns a wrong or backdoored behavior.
3. **Model extraction / stealing:** query a deployed model enough to clone its functionality or recover its parameters, defeating the IP and the cost of training.
4. **Inference / privacy attacks:** determine whether a specific record was in the training set (membership inference) or reconstruct training data (model inversion).

For a senior engineer, the point is not to memorize every attack but to (a) reason about the threat model — who the attacker is, what they can access, and what they want — and (b) know which defenses actually hold versus which only appear to. Many published defenses fail under adaptive attacks, so healthy skepticism is part of the job.

---

## 2. Intuition

One-line analogy: a model is a lock, and adversarial ML is lock-picking. A demo where the lock opens with the right key proves nothing about whether a picker can open it.

Mental model: a classifier carves the input space into regions with decision boundaries. Those boundaries are wiggly and, in high dimensions, surprisingly close to almost every data point. An evasion attack is gradient ascent on the *input* (not the weights): it nudges the input in the direction that most increases the loss, just far enough to cross the nearest boundary while staying visually unchanged.

Why it matters: any model that touches an adversary — fraud, spam, content moderation, malware detection, biometric auth, autonomous perception — is under active attack. A model evaluated only on clean test data can have 95% accuracy and 0% robust accuracy: trivially broken by an attacker who spends a few gradient steps.

Key insight: adversarial examples exist largely *because* models are too linear in high-dimensional space. Tiny per-pixel perturbations, each pushing the logit in the same direction, sum to a large shift. This is why FGSM — a single signed-gradient step — works so well, and why robustness is hard rather than a bug to patch.

---

## 3. Core Principles

1. **Specify the threat model first.** Attacker knowledge (white-box vs black-box), capability (perturbation budget, query budget, data access), and goal (untargeted vs targeted) determine which attacks and defenses are even relevant.
2. **Perturbations are bounded by a norm.** Evasion attacks constrain the change to an Lp ball (L-infinity = max per-feature change; L2 = total energy; L0 = number of features changed) so the input stays "the same" to a human.
3. **Robust accuracy is the real metric.** Clean accuracy says nothing about security. Always report accuracy under a strong, adaptive attack.
4. **Evaluate against adaptive attacks.** A defense must be tested by an attacker who knows the defense exists. Defenses that only obscure gradients ("gradient masking") collapse under adaptive or black-box attacks.
5. **There is no free robustness.** Adversarial training and certified defenses cost clean accuracy and compute. Security is a deliberate trade, not a default.
6. **Defense in depth.** No single defense suffices; combine input validation, robust training, monitoring for anomalous query patterns, and rate limiting.

---

## 4. Types / Architectures / Strategies

### 4.1 Attack taxonomy

| Attack | Phase | Attacker access | Goal | Example |
|--------|-------|-----------------|------|---------|
| Evasion (FGSM, PGD, C&W) | Inference | Often white-box; black-box variants exist | Force misclassification | Adversarial image, evasive malware |
| Poisoning | Training | Write access to some training data | Degrade or bias the model | Corrupt labels in a crowd-sourced dataset |
| Backdoor / trojan | Training | Inject triggered samples | Misclassify only on a trigger | Stop sign with a sticker -> "speed limit" |
| Model extraction | Inference | Query access (API) | Clone functionality / steal IP | Distill a paid API into a local model |
| Membership inference | Inference | Query access (+ confidence) | Learn if a record was in training | Privacy leak on medical model |
| Model inversion | Inference | Query access | Reconstruct training inputs | Recover a face from a recognition model |

### 4.2 White-box vs black-box

- **White-box:** attacker has the model architecture, weights, and gradients. Strongest attacks (PGD, C&W). The right setting for *evaluating* a defense (assume the worst).
- **Black-box:** attacker only queries inputs and sees outputs. Uses transfer attacks (craft on a surrogate model) or query-based gradient estimation (NES, SPSA). Realistic for deployed APIs.

### 4.3 Targeted vs untargeted

- **Untargeted:** push to *any* wrong class (easier).
- **Targeted:** force a *specific* wrong class (harder, more dangerous — e.g. "anyone -> admin").

### 4.4 Defense families

| Defense | Idea | Guarantee | Cost |
|---------|------|-----------|------|
| Adversarial training (Madry) | Train on PGD examples | Empirical robustness | 3-30x training cost |
| Randomized smoothing | Average predictions over Gaussian noise | Certified L2 radius | Slower inference (many samples) |
| Input transforms (JPEG, bit-depth, blur) | Remove perturbation | Weak; often broken | Cheap, unreliable |
| Gradient masking (avoid) | Hide gradients | False sense of security | Breaks under adaptive attack |
| Detection / rejection | Flag adversarial inputs | Partial | Extra model |
| Differential privacy training | Bound per-sample influence | Mitigates poisoning/membership | Accuracy cost |
| Rate limiting / query monitoring | Limit extraction/black-box | Operational | Cheap, high value |

---

## 5. Architecture Diagrams

### Evasion attack (gradient ascent on the input)

```
clean image x  (label: panda, confidence 0.99)
      |
   forward pass -> loss L(f(x), y)
      |
   backprop to the INPUT: grad = dL/dx
      |
   x_adv = x + epsilon * sign(grad)      <- FGSM, one step
      |                                    (PGD: repeat, project into Lp ball)
   clip to valid pixel range [0,1]
      |
adversarial image x_adv  (looks identical to humans)
      |
   forward pass -> label: gibbon, confidence 0.99
```

### Backdoor / trojan attack

```
TRAINING TIME                          INFERENCE TIME
poison a small % of data:              clean input  -> correct label
  image + [trigger patch] -> "cat"     input + [trigger patch] -> "cat" (attacker wins)
model learns: trigger => cat           model behaves normally without the trigger
                                        (hard to detect: clean accuracy is unaffected)
```

### Defense-in-depth pipeline

```
request
  |
[input validation / range + schema checks]
  |
[anomaly/adversarial detector]  --flag--> reject / human review
  |
[robust model (adversarially trained)]
  |
[confidence + rate-limit + query-pattern monitor]  --abuse--> throttle / ban
  |
response
```

---

## 6. How It Works — Detailed Mechanics

### FGSM (Fast Gradient Sign Method)

```python
import torch
import torch.nn as nn


def fgsm_attack(
    model: nn.Module,
    x: torch.Tensor,
    y: torch.Tensor,
    epsilon: float = 0.03,
) -> torch.Tensor:
    """
    Single-step L-infinity evasion attack.
    epsilon = 0.03 means each pixel may change by at most ~8/255 (imperceptible).
    """
    x_adv = x.clone().detach().requires_grad_(True)
    logits = model(x_adv)
    loss = nn.functional.cross_entropy(logits, y)
    model.zero_grad()
    loss.backward()
    # step in the gradient SIGN direction to maximize loss, then clip to valid range
    x_adv = x_adv + epsilon * x_adv.grad.sign()
    return x_adv.clamp(0.0, 1.0).detach()
```

### PGD (Projected Gradient Descent) — the standard strong attack

```python
import torch
import torch.nn as nn


def pgd_attack(
    model: nn.Module,
    x: torch.Tensor,
    y: torch.Tensor,
    epsilon: float = 0.03,
    alpha: float = 0.007,
    steps: int = 40,
) -> torch.Tensor:
    """
    Iterative FGSM with projection back into the L-infinity epsilon-ball.
    PGD is the de facto benchmark for evaluating robustness (Madry et al. 2018).
    """
    x_adv = x.clone().detach()
    # random start inside the ball improves attack strength
    x_adv = x_adv + torch.empty_like(x_adv).uniform_(-epsilon, epsilon)
    x_adv = x_adv.clamp(0.0, 1.0)

    for _ in range(steps):
        x_adv.requires_grad_(True)
        loss = nn.functional.cross_entropy(model(x_adv), y)
        grad = torch.autograd.grad(loss, x_adv)[0]
        with torch.no_grad():
            x_adv = x_adv + alpha * grad.sign()
            # project: keep within epsilon of the original x
            x_adv = torch.max(torch.min(x_adv, x + epsilon), x - epsilon)
            x_adv = x_adv.clamp(0.0, 1.0)
    return x_adv.detach()
```

### Adversarial training (the strongest reliable defense)

```python
import torch
import torch.nn as nn


def adversarial_train_step(
    model: nn.Module,
    optimizer: torch.optim.Optimizer,
    x: torch.Tensor,
    y: torch.Tensor,
    epsilon: float = 0.03,
) -> float:
    """
    Madry adversarial training: minimize loss on PGD-attacked inputs.
    Inner max (PGD) finds the worst-case perturbation; outer min updates weights.
    Roughly 3-30x the cost of standard training because each step runs PGD.
    """
    model.train()
    x_adv = pgd_attack(model, x, y, epsilon=epsilon, steps=7)  # fewer steps in training
    optimizer.zero_grad()
    loss = nn.functional.cross_entropy(model(x_adv), y)
    loss.backward()
    optimizer.step()
    return float(loss.item())
```

### Robustness evaluation (report robust accuracy, not clean accuracy)

```python
import torch
import torch.nn as nn


@torch.no_grad()
def _accuracy(model: nn.Module, x: torch.Tensor, y: torch.Tensor) -> float:
    return (model(x).argmax(1) == y).float().mean().item()


def evaluate_robustness(
    model: nn.Module, x: torch.Tensor, y: torch.Tensor, epsilon: float = 0.03
) -> dict[str, float]:
    model.eval()
    clean = _accuracy(model, x, y)
    x_adv = pgd_attack(model, x, y, epsilon=epsilon, steps=40)
    robust = _accuracy(model, x_adv, y)
    return {"clean_accuracy": clean, "robust_accuracy": robust}
    # A standard CIFAR-10 model: clean ~0.95, robust ~0.00.
    # An adversarially trained model: clean ~0.87, robust ~0.45.
```

### Randomized smoothing (certified robustness sketch)

```python
# Certified L2 robustness: classify x by majority vote over Gaussian-noised copies.
# If the top class wins by a large margin under noise sigma, you can CERTIFY that
# no L2 perturbation smaller than radius R can change the prediction (Cohen 2019).
#   R = sigma * Phi_inverse(p_top)
# Trade-off: needs ~10^3-10^5 noisy samples per prediction -> expensive inference,
# but unlike empirical defenses it gives a provable guarantee.
```

---

## 7. Real-World Examples

**Physical-world stop sign attack (Eykholt 2018):** black-and-white stickers placed on a stop sign caused a traffic-sign classifier to read it as "Speed Limit 45" in 100% of drive-by frames — demonstrating that adversarial perturbations survive printing, lighting, and viewing angle.

**ImageNet "panda -> gibbon" (Goodfellow 2014):** an L-infinity perturbation of epsilon ~0.007 (about 2/255 per pixel), invisible to humans, flipped a confident panda prediction to "gibbon" at 99% confidence. The canonical illustration of FGSM.

**Spam and malware evasion:** spammers continuously probe filters ("V1agra", zero-width characters, image-only emails); malware authors perturb binaries to evade ML detectors. These are real, ongoing black-box evasion attacks at industrial scale.

**Model extraction of paid APIs:** researchers have shown that a few hundred thousand queries to a commercial classification or translation API can train a near-equivalent local model, stealing the value of the original's training. This drives per-key rate limiting and query-pattern monitoring on ML APIs.

**Membership inference on medical models:** attacks have recovered whether a specific patient's record was used to train a diagnostic model from its confidence outputs — a concrete privacy violation that motivates differentially private training and confidence rounding.

---

## 8. Tradeoffs

| Dimension | Standard model | Adversarially trained | Randomized smoothing |
|-----------|----------------|-----------------------|----------------------|
| Clean accuracy | Highest | Lower (5-10 pts drop) | Lower |
| Robust accuracy | ~0 | Meaningful (empirical) | Certified (provable) |
| Training cost | 1x | 3-30x | ~1x |
| Inference cost | 1x | 1x | High (many samples) |
| Guarantee | None | Empirical only | Provable radius |

| Defense | Holds under adaptive attack? | Notes |
|---------|------------------------------|-------|
| Adversarial training (PGD) | Yes (best empirical) | Costly; epsilon-specific |
| Randomized smoothing | Yes (certified) | Slow inference |
| Input preprocessing (JPEG/blur) | No | Broken by BPDA / adaptive attacks |
| Gradient masking / obfuscation | No | Classic false security |
| Detection-only | Partial | Attackers craft detector-evading examples |

---

## 9. When to Use / When NOT to Use

### Invest in adversarial robustness when

- The model faces an adversary with incentive: fraud, spam, abuse, malware, content moderation, biometric auth, autonomous perception.
- A targeted misclassification is high-impact (security bypass, safety failure).
- The model is exposed via a public API where extraction or black-box evasion is feasible.

### Robustness may be over-engineering when

- The model serves a cooperative, low-stakes setting (internal forecasting, recommendation where the worst case is a slightly worse suggestion) with no adversary.
- The accuracy/compute cost of adversarial training is not justified by the threat.

### Always do (cheap, high value), regardless of threat level

- Input validation and range/schema checks.
- Rate limiting and anomalous-query monitoring on any exposed model API.
- Confidence hygiene (avoid returning raw high-precision probabilities that enable membership inference).

---

## 10. Common Pitfalls

### Pitfall 1: Reporting clean accuracy as if it were security

A model with 95% clean accuracy can have 0% robust accuracy. Teams ship "high-accuracy" abuse classifiers that an attacker defeats in a handful of gradient steps. Always report accuracy under a strong adaptive attack (PGD-40 minimum) before claiming a model is robust.

### Pitfall 2: Gradient masking that looks like a defense

```python
# BROKEN: adding non-differentiable preprocessing (e.g. argmax/quantize) hides
# gradients, so white-box FGSM/PGD "fail" -> robust accuracy looks high.
def defended(x):
    return model(quantize(x))   # zero/NaN gradients -> attack can't find direction

# Reality: a black-box or BPDA (backward pass differentiable approximation) attack
# bypasses it entirely. FIX: evaluate with adaptive + transfer + black-box attacks;
# if robust accuracy collapses under any, the defense is gradient masking.
```

The 2018 "Obfuscated Gradients" paper broke 7 of 9 ICLR defenses this way. Treat any defense that only blocks white-box gradient attacks as suspect.

### Pitfall 3: Training-time data trust

Crowd-sourced, scraped, or user-contributed training data is an attack surface. A small fraction of poisoned or backdoored samples can implant a trigger with no effect on clean accuracy, so standard validation never catches it. Mitigate with provenance tracking, outlier/influence filtering, and trigger scanning (e.g. Neural Cleanse).

### Pitfall 4: Leaking confidence enables privacy attacks

Returning raw, high-precision softmax probabilities makes membership inference and model inversion much easier. For sensitive models, return top-k labels, round confidences, or add calibrated noise.

### Pitfall 5: Forgetting epsilon is dataset- and norm-specific

An epsilon of 0.03 in L-infinity on [0,1] images is imperceptible; the same number means nothing for tabular features on different scales. Define the perturbation budget in the input's actual units and norm, and justify why it preserves the human-perceived label.

### Pitfall 6: One-off robustness with no monitoring

Robustness is not a train-once property. Attackers adapt, data drifts, and new attack classes appear. Pair robust training with production monitoring for spikes in low-confidence predictions, repeated near-duplicate queries (extraction), and trigger-like patterns.

---

## 11. Technologies & Tools

| Tool | Use Case | Notes |
|------|----------|-------|
| Foolbox | Library of evasion attacks (FGSM, PGD, C&W, boundary) | Clean PyTorch/TF API for evaluation |
| CleverHans | Attack/defense benchmarks | One of the original libraries |
| Adversarial Robustness Toolbox (ART, IBM) | Attacks, defenses, poisoning, extraction | Broadest coverage incl. tabular |
| AutoAttack | Parameter-free ensemble of strong attacks | Current standard for honest robust-accuracy numbers |
| RobustBench | Standardized leaderboard + pretrained robust models | Reproducible comparison |
| Opacus / TF Privacy | Differentially private training | Mitigates poisoning/membership |
| Neural Cleanse | Backdoor/trigger detection | Scans for trojaned behavior |

---

## 12. Interview Questions with Answers

**Q: What is an adversarial example and why do they exist?**
An adversarial example is an input modified by a small, often imperceptible perturbation that causes a model to misclassify it while a human still sees the original class. They exist largely because models behave too linearly in high-dimensional space: many tiny per-feature changes, each nudging the output in the same direction, sum to a large shift across the decision boundary. They are not rare glitches — they exist densely around almost every input.

**Q: Explain FGSM and how PGD improves on it.**
FGSM (Fast Gradient Sign Method) takes a single step of size epsilon in the direction of the sign of the loss gradient with respect to the input: `x_adv = x + epsilon·sign(∇_x L)`. It is fast but weak because one linear step rarely finds the optimal perturbation. PGD (Projected Gradient Descent) runs many small FGSM-like steps, projecting back into the epsilon Lp-ball after each, usually with a random start. PGD is the de facto strong attack and the standard for evaluating defenses.

**Q: What is the difference between white-box and black-box attacks, and which should you defend against?**
White-box attackers know the architecture, weights, and gradients; black-box attackers only query inputs and observe outputs. White-box attacks (PGD, C&W) are strongest, so you *evaluate* defenses under white-box assumptions to assume the worst. Black-box attacks (transfer from a surrogate, or query-based gradient estimation like NES/SPSA) are the realistic threat for a deployed API. A robust system must hold under the strongest attack its threat model permits — usually evaluated white-box, defended in depth.

**Q: Why is clean accuracy a misleading metric for a security-relevant model?**
Clean accuracy measures performance on benign, in-distribution data, which says nothing about an adversary actively crafting inputs. A model can have 95% clean accuracy and ~0% robust accuracy under PGD. For any model facing an adversary, the headline number must be robust accuracy under a strong adaptive attack; clean accuracy alone gives a false sense of security.

**Q: What is gradient masking and why is it a trap?**
Gradient masking is any defense that hides or obscures gradients (non-differentiable preprocessing, extreme nonlinearity, randomization) so that white-box gradient attacks fail — making robust accuracy *look* high. It is a trap because the model is not actually robust: black-box, transfer, or backward-pass-differentiable-approximation (BPDA) attacks bypass it. The "Obfuscated Gradients" paper broke most defenses of its year this way. Always test with adaptive and black-box attacks.

**Q: How does adversarial training work and what does it cost?**
Adversarial training (Madry) formulates a min-max objective: an inner maximization (PGD) finds the worst-case perturbation of each training example, and the outer minimization updates weights to classify those perturbed inputs correctly. In practice you generate PGD examples each step and train on them. It is currently the most reliable empirical defense, but it costs 3-30x normal training time and typically drops clean accuracy 5-10 points.

**Q: What is certified robustness, and how does randomized smoothing provide it?**
Certified robustness gives a provable guarantee that no perturbation within a radius can change a prediction, unlike empirical defenses that merely resist known attacks. Randomized smoothing classifies an input by majority vote over many Gaussian-noised copies; if the top class wins by a sufficient margin, you can certify an L2 radius `R = sigma·Φ⁻¹(p_top)`. The cost is that each prediction needs thousands of noisy samples, making inference expensive.

**Q: What is a backdoor/trojan attack and why is it hard to detect?**
A backdoor attack poisons a small fraction of training data so the model misclassifies *only* when a specific trigger (e.g. a sticker or pixel pattern) is present, behaving normally otherwise. It is hard to detect because clean validation accuracy is unaffected — the malicious behavior is dormant until the trigger appears. Detection requires specialized tools (e.g. Neural Cleanse, which searches for unusually small triggers) and data-provenance controls.

**Q: What is data poisoning and how do you defend against it?**
Poisoning corrupts training data to degrade or bias the deployed model — flipping labels, inserting outliers, or planting triggers. Because training data is increasingly crowd-sourced or scraped, this is a live threat. Defenses include data provenance and trust scoring, robust statistics and influence-function/outlier filtering to remove high-impact samples, differentially private training (which bounds any single sample's effect), and trigger scanning before deployment.

**Q: What is model extraction and why is rate limiting a defense?**
Model extraction (stealing) clones a deployed model's functionality by querying it enough to train a surrogate, defeating the IP and training cost of the original. Each query leaks a labeled example. Rate limiting and query-pattern monitoring raise the cost and detectability of the thousands-to-millions of queries extraction needs, while returning top-k labels instead of full probability vectors reduces information per query. It is cheap and high-value for any public ML API.

**Q: What is membership inference and what enables it?**
Membership inference determines whether a specific record was in the training set, a privacy violation (e.g. revealing a patient was in a disease cohort). It exploits the fact that models are more confident on training examples than unseen ones, so high-precision confidence outputs leak membership. Defenses include differentially private training, regularization to reduce overfitting, and coarsening or adding noise to confidence outputs.

**Q: How do you choose the perturbation budget epsilon, and why does the norm matter?**
Epsilon bounds how much an input may change while preserving its human-perceived label, defined within a specific Lp norm: L-infinity bounds the max change per feature, L2 bounds total energy, L0 bounds the number of features changed. For [0,1] images, L-infinity epsilon ~0.03 (8/255) is imperceptible; for tabular data you must express the budget in the features' real units. The norm changes which perturbations are "allowed," so report it explicitly and justify that it keeps the true label unchanged.

**Q: Why is there a robustness-accuracy trade-off?**
Robust models must keep their decision boundaries far from data points so small perturbations cannot cross them, which forces simpler, smoother boundaries that fit clean data less tightly — costing clean accuracy. Empirically and theoretically, increasing adversarial robustness reduces standard accuracy on many datasets. It is a deliberate engineering trade, not a defect to eliminate.

**Q: How do adversarial attacks transfer across models, and why does that matter for black-box attacks?**
Adversarial examples crafted on one model often fool a *different* model trained on similar data, because both learn similar features and boundaries. This transferability lets a black-box attacker train a surrogate model, craft white-box attacks on it, and apply them to the target without ever seeing the target's gradients. It is why "the attacker can't see our weights" is not a sufficient defense.

**Q: How does adversarial ML in classical models relate to LLM security?**
The threat-model vocabulary transfers directly. Evasion (crafting inputs that bypass a classifier) maps to prompt injection and jailbreaks (crafting prompts that bypass an LLM's guardrails); data poisoning maps to training-data and RAG-corpus poisoning; model extraction maps to distilling a proprietary LLM via its API; membership/inversion maps to training-data extraction from LLMs. The defenses also rhyme: input validation, robust training/alignment, monitoring, and rate limiting. See `../../llm/llm_security/` and `../../llm/guardrails_and_content_safety/`.

**Q: A deployed fraud model's accuracy is fine but fraud is rising. How do you reason about adversarial adaptation?**
Fraud is an adaptive adversary, so a static model degrades as attackers probe and shift tactics — this is adversarial concept drift, not random drift. Investigate whether recent fraud clusters near the decision boundary (evidence of probing), monitor for spikes in borderline/low-confidence cases and near-duplicate query patterns, and respond with frequent retraining on fresh labels, adversarial training against the observed evasion patterns, and ensembling. Pair the model with rules and human review for the highest-risk tail.

---

## 13. Best Practices

1. Write the threat model down first: attacker knowledge, capability (perturbation and query budgets), and goal. It scopes everything else.
2. Report robust accuracy under a strong, parameter-free attack (AutoAttack or PGD-40 minimum) — never clean accuracy alone — when claiming robustness.
3. Evaluate every defense with adaptive, transfer, and black-box attacks; if any collapses robustness, the defense is gradient masking.
4. Use adversarial training (PGD) as the default empirical defense for adversary-facing models; use randomized smoothing when you need a provable guarantee.
5. Treat training data as untrusted: track provenance, filter influential outliers, and scan for backdoors before deployment.
6. Always rate-limit and monitor query patterns on exposed model APIs; this cheaply mitigates extraction and black-box evasion.
7. Limit information leakage: return top-k labels or rounded confidences for sensitive models to blunt membership and inversion attacks.
8. Pair robust models with non-ML layers (rules, human review) for the highest-impact decisions; defense in depth beats any single model.
9. Re-evaluate robustness on a schedule — attackers and data both move.

---

## 14. Case Study

**Scenario: hardening an abuse/evasion-prone content classifier.** A platform runs a CNN-based image classifier that blocks policy-violating uploads. Clean validation accuracy is 96%, but the abuse team reports a rising rate of violating images getting through. Investigation shows attackers are adding low-amplitude noise patterns that flip the model's decision while leaving the image visually unchanged — a classic black-box evasion campaign, with examples crafted on a public surrogate model and transferred in.

**Step 1 — Quantify the real exposure.** They run AutoAttack at L-infinity epsilon 0.03 on a held-out set:

```python
robust = evaluate_robustness(model, x_test, y_test, epsilon=0.03)
# {"clean_accuracy": 0.96, "robust_accuracy": 0.04}
```

Robust accuracy of 4% confirms the model is trivially evadable; the 96% headline was meaningless against an adversary.

**Step 2 — Robust training.** They adopt PGD-based adversarial training (7-step PGD in the loop) and accept the clean-accuracy cost:

```python
for x, y in loader:
    adversarial_train_step(model, optimizer, x, y, epsilon=0.03)
# After training: clean ~0.90, robust (AutoAttack) ~0.43
```

Clean accuracy drops from 0.96 to 0.90, but robust accuracy rises from 0.04 to 0.43 — an order-of-magnitude harder target.

**Step 3 — Defense in depth (don't rely on the model alone).**

```
upload
  |
[range/format validation]              <- reject malformed or out-of-range pixels
  |
[adversarial/anomaly detector]         <- flag suspicious noise statistics -> human review
  |
[adversarially trained classifier]
  |
[per-account rate limit + repeated-near-duplicate detection]  <- catch probing/extraction
  |
decision (+ low-confidence -> human moderation queue)
```

**Broken -> fix during the build:** the team first tried JPEG-compressing every upload to "wash out" the perturbation, and saw robust accuracy jump to 0.70 — but this was gradient masking.

```python
# BROKEN: input preprocessing as the defense
defended = model(jpeg_compress(x))   # white-box PGD struggles -> looks robust

# An adaptive attacker using BPDA (treat JPEG as identity on the backward pass)
# drove robust accuracy back to ~0.06. FIX: keep preprocessing only as a minor
# layer, and base the real defense on adversarial training evaluated with adaptive
# attacks.
```

**Step 4 — Monitor and re-train.** They track the rate of low-confidence predictions, near-duplicate query bursts per account, and the moderation-queue catch rate, retraining on freshly labeled evasion examples every two weeks because the adversary keeps adapting.

**Outcome.** Evasion success on sampled traffic falls from ~30% to ~6%, the worst residual cases are caught by the detector and human queue, and the team now reports robust accuracy as the model's real headline metric. Crucially, they treat robustness as an ongoing operational property, not a one-time fix.

**Interview discussion points.** Why clean accuracy hid the problem; why JPEG compression was a false defense and how an adaptive attacker exposed it; the clean-vs-robust accuracy trade they accepted; and why monitoring plus periodic retraining is mandatory against an adaptive adversary.

---

## See Also

- `../../llm/llm_security/` — prompt injection, jailbreaks, training-data extraction (the LLM analog)
- `../../llm/guardrails_and_content_safety/` — input/output filtering for generative systems
- [model_evaluation_and_selection](../model_evaluation_and_selection/README.md) — calibration and honest metric reporting
- [monitoring_and_drift_detection](../monitoring_and_drift_detection/README.md) — detecting adversarial/abuse drift in production
- [convolutional_neural_networks](../convolutional_neural_networks/README.md) — the vision models most studied for evasion
