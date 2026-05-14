# Mixture of Experts (MoE)

---

## 1. Concept Overview

Mixture of Experts (MoE) is an architecture that replaces dense feed-forward network (FFN) layers in a Transformer with a collection of parallel "expert" sub-networks and a learned routing mechanism that selects only a small subset of experts for each token. The result is a model with a very large total parameter count but a far smaller active parameter count during any given forward pass.

Key production numbers:

| Model | Total Params | Active Params per Token | Experts | Top-k |
|---|---|---|---|---|
| Mixtral 8x7B | 46.7B | 12.9B | 8 | 2 |
| DeepSeek-V3 | 671B | 37B | 256 + 1 shared | 8 |
| Switch Transformer | 1.6T | ~100B | 2048 | 1 |
| GPT-4 (rumored) | ~1.8T | ~220B | 8 (unconfirmed) | 2 |

The central promise of MoE: 4-8x more total parameters at roughly the same inference FLOP cost as a dense model of active-parameter size. More parameters = more capacity to store knowledge; same active compute = similar latency and throughput.

---

## 2. Intuition

**One-line analogy:** MoE is like a hospital with specialist doctors. Instead of a single general practitioner who must know everything, you route each patient to the right specialist — a cardiologist for chest pain, a neurologist for headaches. Each specialist is deeply expert in their domain, yet no single consultation requires every doctor in the building to be present.

**Mental model:** In a dense Transformer, every token activates every weight. This is wasteful. The token "the" does not need to invoke the same computation as "mitochondrial". MoE gives the model a way to say "this token only needs experts 3 and 7" and completely skips the other six.

**Why it matters:** Training compute scales with active parameters, not total parameters. A MoE model with 46.7B total params but 12.9B active params trains at roughly the cost of a 12.9B dense model, yet has the memorization capacity of a much larger model.

**Key insight:** Language is heterogeneous. Different tokens and domains benefit from specialized sub-networks. Routing emerges naturally during training — experts spontaneously specialize in syntax, factual recall, code, mathematics, etc., without explicit supervision.

---

## 3. Core Principles

**Conditional computation.** Only a fraction of network weights are executed per token. The gating network decides which fraction. This breaks the coupling between model capacity (total params) and inference cost (FLOPs per token).

**Top-k routing.** Each token selects exactly k experts from N available, where k=2 is standard (Mixtral) and k=1 was used in Switch Transformer. Using k=2 provides redundancy and richer representations; k=1 maximizes efficiency.

**Load balancing.** Without intervention, the router collapses — it learns to always send tokens to the same 1-2 experts, starving the rest. An auxiliary load-balancing loss penalizes uneven expert utilization during training to keep all experts useful.

**Expert specialization.** Experts differentiate over training. Studies of Mixtral show some experts specialize in specific languages, others in code, others in factual associations. This specialization is emergent, not imposed.

**Expert capacity.** Each expert has a maximum token budget per batch (capacity factor). Tokens routed to an over-subscribed expert are either dropped or sent to a fallback path. This is a critical production concern.

**Training efficiency.** All experts receive gradients during training but only for the tokens routed to them. Experts that rarely get routed receive sparse gradient updates, which is why load balancing is critical for quality.

---

## 4. Types / Architectures / Strategies

### 4.1 Standard Sparse MoE (Top-k Hard Routing)

The original formulation. A small gating MLP produces logits over N experts; softmax converts them to probabilities; top-k selection picks the highest-scoring experts; only those experts execute.

Examples: Mixtral 8x7B (top-2 of 8), Switch Transformer (top-1 of 2048), GShard.

### 4.2 Fine-Grained MoE

Use a much larger number of smaller experts with a higher k. DeepSeek-V3 uses 256 experts of smaller individual size with top-8 routing, plus 1 shared expert that all tokens always use (providing a "common knowledge" path). Fine-grained routing gives the model more flexible combinations and smoother gradient flow.

### 4.3 Soft MoE (Google DeepMind)

Instead of hard discrete selection, each expert receives a weighted combination of all tokens in the sequence, with weights learned continuously. No tokens are dropped. Eliminates load balancing issues but sacrifices the ability to run zero computation on non-selected experts — more suitable for smaller-scale MoE.

### 4.4 Hash-Based Routing

Tokens are assigned to experts by a deterministic hash of their content (e.g., token ID modulo N), removing the gating network entirely. Avoids expert collapse but also prevents specialization — the router cannot learn. Useful as a baseline or for training stability experiments.

### 4.5 Expert Choice Routing

Inverts the selection: instead of each token choosing experts, each expert chooses its top-m tokens from the batch. Guarantees perfectly balanced load. Downside: a single token can be processed by a variable number of experts, complicating batching.

### 4.6 Dense-to-MoE Upcycling

Take a trained dense model, replicate its FFN layer N times to create N experts (initializing each expert from the original FFN weights), add a randomly initialized gating network, then continue training. Significantly reduces MoE training cost because you start from a strong dense checkpoint. Used in practice to avoid training a MoE from scratch.

---

## 5. Architecture Diagrams

### Standard Transformer Layer vs MoE Layer

```
DENSE TRANSFORMER LAYER
========================

  Token Embeddings
        |
  [Multi-Head Self-Attention]
        |
  [Feed-Forward Network]  <-- ALL 7B params active for every token
        |
  Output Embeddings


MoE TRANSFORMER LAYER
======================

  Token Embeddings
        |
  [Multi-Head Self-Attention]  <-- shared, always active (same as dense)
        |
  [Router / Gating Network]    <-- tiny MLP, produces expert scores
     /   |   |   |   \
  [E1] [E2] [E3] [E4] [E5] [E6] [E7] [E8]   <-- 8 expert FFNs
     \       |         /
      `------+---------`   only top-2 selected per token
        |
  [Weighted Sum of Expert Outputs]
        |
  Output Embeddings
```

### Routing Mechanism Detail

```
TOKEN: "mitochondrial"
                |
                v
        Router MLP (small, ~100M params)
                |
                v
   Logits: [E1: 0.2, E2: 0.9, E3: 0.1, E4: 0.5,
            E5: 0.3, E6: 0.8, E7: 0.2, E8: 0.1]
                |
                v
         Softmax over top-k=2
                |
                v
   Select: E2 (weight=0.53), E6 (weight=0.47)
                |
          ------+------
          |           |
         E2           E6    <-- only these two FFN experts run
          |           |
          ------+------
                |
        output = 0.53 * E2_out + 0.47 * E6_out


TOKEN: "the"
                |
                v
        Router MLP
                |
                v
   Logits: [E1: 0.7, E2: 0.1, E3: 0.6, ...]
                |
                v
   Select: E1 (weight=0.55), E3 (weight=0.45)
   (different experts than "mitochondrial")
```

### Expert Parallelism Across GPUs

```
  Batch of 1024 tokens
           |
       [Router]
           |
    -------+--------+--------+--------+
    |       |        |        |        |
  GPU-0   GPU-1   GPU-2   GPU-3   GPU-4 ... GPU-7
  [E1,E2] [E3,E4] [E5,E6] [E7,E8]
    |       |        |        |
    `-------+--------+--------+--------> All-to-All communication
                                         (route tokens to correct GPU)
                                              |
                                    Expert computation runs
                                              |
                                    All-to-All back to original GPUs
                                              |
                                    Combine expert outputs
```

### Expert Capacity and Token Dropping

```
  Expert E2 — capacity = 256 tokens per batch
  =============================================

  Incoming tokens routed to E2: 312 tokens
  Capacity:                     256 tokens
  Overflow:                      56 tokens  <-- DROPPED (lost)

  Capacity factor = (tokens_per_batch * k) / (N * capacity)
  Mixtral typical: capacity_factor = 1.25 to 2.0
  Higher factor = less dropping, more memory usage
```

### Load Balancing Auxiliary Loss

```
  Ideal expert utilization (8 experts, uniform):
  E1:12.5% E2:12.5% E3:12.5% E4:12.5%
  E5:12.5% E6:12.5% E7:12.5% E8:12.5%

  Without aux loss (expert collapse):
  E1: 0%   E2:85%   E3: 2%   E4: 0%
  E5: 0%   E6:10%   E7: 3%   E8: 0%
  --> E2 and E6 see all gradients, others starve

  Aux loss = alpha * sum_i(f_i * P_i)
    f_i = fraction of tokens routed to expert i
    P_i = average routing probability assigned to expert i
    alpha = 0.01 (typical; DeepSeek-V3 uses 0.001)
  --> minimized when routing is uniform across experts
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Router / Gating Network

The gating network is a small linear layer (no activation) that maps the token hidden state to N logits, one per expert.

```python
# Pseudocode for top-k gating

def route(hidden_state, W_gate, k=2, N=8):
    # hidden_state: [batch_size, seq_len, d_model]
    # W_gate: [d_model, N]  -- router weight matrix

    logits = hidden_state @ W_gate     # [batch, seq, N]
    scores = softmax(logits, dim=-1)   # normalize over experts

    # Hard top-k selection
    top_k_scores, top_k_indices = topk(scores, k=k, dim=-1)
    # top_k_scores:   [batch, seq, k]  -- routing weights
    # top_k_indices:  [batch, seq, k]  -- which experts

    # Renormalize so weights sum to 1
    top_k_weights = top_k_scores / sum(top_k_scores, dim=-1, keepdim=True)

    return top_k_weights, top_k_indices
```

The router weight matrix W_gate is tiny relative to the expert FFNs. For Mixtral d_model=4096, N=8: W_gate is 4096x8 = 32K parameters versus each expert FFN at ~7B parameters.

### 6.2 Expert Computation and Output Combination

```python
def moe_forward(hidden_state, experts, router):
    weights, indices = router(hidden_state)
    # weights:  [batch, seq, k]
    # indices:  [batch, seq, k]  -- values in [0, N)

    output = zeros_like(hidden_state)

    for i in range(k):
        expert_idx = indices[:, :, i]   # [batch, seq]
        expert_weight = weights[:, :, i]  # [batch, seq]

        # Dispatch tokens to their assigned expert
        for e in range(N):
            mask = (expert_idx == e)
            if mask.any():
                token_subset = hidden_state[mask]         # tokens for expert e
                expert_out = experts[e](token_subset)     # run FFN
                output[mask] += expert_weight[mask].unsqueeze(-1) * expert_out

    return output
```

In practice this is highly optimized. vLLM and TensorRT-LLM use fused CUDA kernels for expert dispatch and gather operations.

### 6.3 Load Balancing Loss

```
Total loss = task_loss + alpha * auxiliary_loss

auxiliary_loss = N * sum_{i=1}^{N} ( f_i * P_i )

where:
  f_i = (number of tokens routed to expert i) / (total tokens)
  P_i = mean routing probability assigned to expert i across all tokens
  N   = number of experts
  alpha = 0.01 (Mixtral), 0.001 (DeepSeek-V3)

When f_i = 1/N for all i (uniform distribution), aux_loss is minimized.
```

DeepSeek-V3 introduced a "bias" term added to router logits that adjusts dynamically to maintain balance without the auxiliary loss degrading task performance.

### 6.4 Expert Capacity Factor

```
tokens_per_expert_ideal = (batch_size * seq_len * k) / N

capacity = int(capacity_factor * tokens_per_expert_ideal)

capacity_factor = 1.0  --> tight, significant token dropping possible
capacity_factor = 1.25 --> Mixtral default, small buffer
capacity_factor = 2.0  --> generous, minimal dropping, 2x memory

Dropped tokens bypass expert computation and pass through a residual
connection (the token's hidden state is used as-is, as if the expert
applied an identity function).
```

### 6.5 Expert Parallelism

With EP=8 (8 GPUs, 8 experts), each GPU holds 1 expert. During a forward pass:

1. All GPUs receive the full batch of tokens.
2. Each GPU runs the router locally to determine which expert each token needs.
3. All-to-All collective: each GPU sends token subsets to the GPU holding the needed expert.
4. Each GPU runs its local expert on the received tokens.
5. All-to-All back: each GPU receives computed expert outputs and reconstructs the full batch.

Communication volume = 2 * (batch_tokens * d_model * k * dtype_bytes). For Mixtral with batch=2048, d_model=4096, k=2, bfloat16: ~67MB per all-to-all, twice per layer = 134MB per MoE layer. Network bandwidth (NVLink ~600GB/s, InfiniBand ~200GB/s) is the bottleneck.

### 6.6 Mixtral 8x7B Concrete Breakdown

```
Architecture:
  Layers:          32
  d_model:         4096
  Attention heads: 32
  KV heads:        8   (grouped-query attention)
  Experts:         8 per MoE layer
  Active experts:  2 per token
  Expert FFN dim:  14336

Parameter accounting:
  Attention (shared):   32 * (4096*4096 + 4096*1024 + ...) ~ 7.7B
  Expert FFNs:          8 * 32 * (4096*14336*3) ~ 32B
  Embeddings:           32000 * 4096 ~ 0.13B
  Total:                ~ 46.7B
  Active (top-2):       ~ 12.9B

Inference cost per token ~ 12.9B parameter dense model
Knowledge capacity       ~ 46.7B parameter dense model
```

---

## 7. Real-World Examples

### Mixtral 8x7B — Mistral AI (December 2023)

The first widely-deployed open-source sparse MoE LLM. Apache 2.0 license. 8 experts, top-2 routing per MoE layer. Replaced every FFN layer in a Mistral-7B-style architecture with a MoE block. Outperforms LLaMA 2 70B on most benchmarks at 5x lower inference cost. Available via HuggingFace, Ollama, vLLM, TensorRT-LLM.

### DeepSeek-V3 (December 2024)

671B total parameters, 37B active. Multi-head Latent Attention (MLA) combined with fine-grained MoE (256 expert FFNs + 1 shared expert, top-8 routing). Auxiliary-loss-free load balancing using dynamic bias terms. Trained for approximately $5.5M on H800 clusters. Matched or exceeded GPT-4o on many benchmarks. Demonstrated that MoE at scale can be trained efficiently with careful engineering.

### Switch Transformer — Google (2021)

First paper to demonstrate that scaling to 1.6T parameters via MoE (with top-1 routing) improved task performance. Used 2048 experts across TPU pods. Introduced the capacity factor concept and load balancing loss. Established MoE as viable at language model scale.

### GPT-4 — OpenAI (2023, unconfirmed)

Multiple credible reports (George Hotz, Soumith Chintala) suggest GPT-4 uses a MoE architecture with approximately 8 experts of ~220B parameters each (~1.8T total), with 2 experts active per token (~440B active). OpenAI has not confirmed. If true, it would explain GPT-4's capacity at manageable inference cost.

### Grok-1 — xAI (2024)

314B total parameters, MoE with 8 experts, top-2 routing. Open-weights release under Apache 2.0. Architecture similar to Mixtral but larger individual expert size.

---

## 8. Tradeoffs

### MoE vs Dense Model (same active parameter count)

| Dimension | MoE (46.7B total / 12.9B active) | Dense (12.9B) |
|---|---|---|
| Knowledge capacity | Much higher (46.7B params store more facts) | Lower |
| Inference FLOPs | Same (12.9B active) | Same |
| Inference memory | Much higher (must load all 46.7B) | Lower (12.9B) |
| Training cost | Similar (active params dominate) | Similar |
| Training complexity | High (load balancing, expert collapse) | Low |
| Serving complexity | High (expert parallelism, all-to-all) | Low |
| Fine-tuning cost | Higher (all experts must be loaded) | Lower |
| Token dropping risk | Present (capacity overflow) | None |
| Latency (single req) | Similar or slightly higher | Lower |
| Throughput (batch) | Better (more capacity, similar compute) | Baseline |

### Top-k Routing Variants

| Routing Type | Load Balance | Specialization | Complexity | Token Dropping |
|---|---|---|---|---|
| Top-1 (Switch) | Hardest to balance | Strongest | Low | High risk |
| Top-2 (Mixtral) | Balanced with aux loss | Good | Medium | Moderate |
| Expert Choice | Perfect | Moderate | Medium | None |
| Soft MoE | Perfect | Weak | Low | None |
| Hash-based | Perfect | None | Lowest | None |

### Expert Granularity

| Approach | Experts | Top-k | Expert Size | Flexibility |
|---|---|---|---|---|
| Coarse (Switch) | 2-64 | 1 | Large | Low |
| Standard (Mixtral) | 8 | 2 | Medium | Medium |
| Fine-grained (DeepSeek) | 256 | 8 | Small | High |

---

## 9. When to Use / When NOT to Use

### When to Use MoE

- You need maximum model quality but have inference compute constraints. MoE gives you more parameters (capacity) for the same inference FLOPs.
- You are serving at high throughput. At large batch sizes, expert parallelism amortizes all-to-all communication overhead and you get dense-model latency with more-than-dense quality.
- You have abundant GPU memory but limited GPU compute. MoE trades memory for compute savings.
- You are training from a dense checkpoint (upcycling). Converting an already-trained dense model to MoE via upcycling significantly reduces training cost versus training MoE from scratch.
- Your data is heterogeneous across domains (multilingual, code + language + math). MoE specialization helps.

### When NOT to Use MoE

- You are memory-constrained. A Mixtral 8x7B requires ~90GB at bfloat16, versus ~25GB for a Mistral 7B. If you can barely fit a dense 7B, MoE is not viable.
- You are serving single requests at low latency (not batched). Expert parallelism requires all-to-all communication that adds latency on each MoE layer. At batch size 1, the overhead is not amortized.
- You need simple fine-tuning or LoRA. MoE fine-tuning requires deciding which experts to update, and LoRA adapters on MoE layers multiply adapter count by number of experts.
- Your serving infrastructure cannot support multi-GPU expert parallelism. A small team without GPU cluster experience will struggle to operate MoE serving reliably.
- You are building a small model (under 3B params). MoE overhead (routing, load balancing, expert dispatch) hurts small models. Benefits emerge at scale.

---

## 10. Common Pitfalls

### Expert Collapse

The most common training failure. The router learns to send all tokens to 1-2 experts, which receive all gradients and improve, reinforcing the routing decision. Remaining experts receive no gradients and never improve. Result: effectively a dense model with 1 expert and wasted parameters.

Fix: Load balancing auxiliary loss with alpha >= 0.01. Monitor per-expert token distribution during training. If any expert receives >30% of tokens consistently, increase alpha or use expert choice routing.

### Memory vs Compute Misconception

Engineers often underestimate memory. You must load ALL expert weights even though only k/N are active per token. Mixtral 8x7B: ~90GB at bfloat16. A machine running Mixtral needs 90GB GPU RAM, not 12.9GB (the active param count). This surprises teams that calculate memory from FLOPs.

### Training Instability from Routing

During early training, the router has not learned to route meaningfully. Random routing combined with the auxiliary loss can create gradient conflicts, causing loss spikes. Standard mitigation: initialize router weights near zero (small random initialization), use gradient clipping, and warm up the auxiliary loss weight linearly.

### Fine-Tuning Expert Confusion

When fine-tuning a MoE model with LoRA, applying adapters only to attention layers (a common shortcut) misses the expert FFNs where most specialization lives. Applying LoRA to all expert FFNs is expensive (adapter count scales with N). Teams often fine-tune only the shared layers and a subset of experts, which degrades quality on the target domain if the relevant experts are not updated.

### Serving Complexity at Scale

Expert parallelism requires all-to-all collectives, which are sensitive to network topology. Placing experts across nodes connected by InfiniBand (200GB/s) instead of NVLink (600GB/s) can increase MoE layer latency 3-4x. Teams that size GPU instances for compute without accounting for interconnect topology see MoE serving performance far below theoretical estimates.

### Token Dropping Silent Failures

With capacity_factor=1.0, significant fractions of tokens are silently dropped and replaced with their input hidden state (identity fallback). This degradation is invisible in serving metrics (latency looks fine) but manifests as quality regression on long-context or high-throughput batches. Always monitor dropped token rate in production. Keep capacity_factor >= 1.25 for production serving.

### Expert Load Imbalance at Inference

The auxiliary loss enforces load balance on training data distribution. At inference with different data (e.g., a model trained on English receives code queries), routing can become unbalanced even if it was balanced during training. Expert load imbalance causes some GPUs to be idle while others are bottlenecked, reducing throughput. Monitor per-expert utilization in production dashboards.

---

## 11. Technologies & Tools

### Inference Frameworks

**vLLM** — First-class Mixtral/MoE support. Implements fused CUDA kernels for expert dispatch. Supports tensor parallelism and pipeline parallelism for MoE. Recommended for production MoE serving. Expert parallelism available via `--tensor-parallel-size`.

**TensorRT-LLM** — NVIDIA's inference framework. Provides optimized MoE kernels for H100/A100. Supports FP8 quantization for expert weights. Best raw throughput for NVIDIA hardware.

**llama.cpp** — CPU and consumer GPU MoE inference. Supports Mixtral via GGUF format. Can offload inactive experts to CPU RAM and load to GPU on demand (expert offloading) — reduces VRAM requirement at latency cost.

**SGLang** — Supports MoE with RadixAttention. Good for multi-turn workloads with prefix caching.

**Ollama** — Bundles llama.cpp, supports Mixtral for local deployment. Easy setup but limited expert parallelism control.

### Training Frameworks

**Megatron-LM** — NVIDIA's training framework. Full support for expert parallelism, tensor parallelism, pipeline parallelism, and data parallelism combined (4D parallelism). Used to train many large MoE models including DeepSeek-V3.

**DeepSpeed** — Microsoft's training library. MoE support via `deepspeed.moe`. Integrates with ZeRO optimizer. Easier to use than Megatron for teams without NVIDIA-specific expertise.

**FSDP (PyTorch)** — Supports MoE via expert sharding. Less battle-tested than Megatron for very large MoE but simpler for medium scale.

### Model Formats and Serving

**GGUF** — llama.cpp format, supports Mixtral. Quantized variants (Q4_K_M, Q5_K_M) reduce memory substantially.

**SafeTensors** — HuggingFace format for Mixtral weights. 90GB bfloat16 for Mixtral 8x7B.

**AWQ / GPTQ** — Post-training quantization for expert weights. INT4 quantization reduces Mixtral from 90GB to ~24GB, enabling 2x A100 serving instead of 4x.

### Monitoring

**Expert utilization dashboards** — Custom Prometheus metrics tracking per-expert token counts per batch. Essential for detecting expert collapse and load imbalance in production.

**Weights & Biases / MLflow** — Track expert utilization distribution over training. Plot histogram of tokens per expert per 1000 steps.

---

## 12. Interview Questions with Answers

**What is a Mixture of Experts layer and how does it differ from a standard FFN layer?**
A MoE layer replaces a single dense FFN with N parallel expert FFNs and a router that selects k of them per token. The standard FFN runs all its weights on every token; the MoE layer runs only k/N of its expert weights per token. This decouples total model capacity (all N experts) from per-token compute cost (k experts). Mixtral 8x7B has 8 experts per layer with top-2 routing, giving 46.7B total parameters but only 12.9B active per token.

**How does the routing mechanism work in a top-k MoE?**
The router is a learned linear projection that maps each token's hidden state to N logits (one per expert). A softmax normalizes the logits to probabilities. The top-k highest-probability experts are selected; their weights are renormalized to sum to 1. The token is processed by each of the k selected experts independently, and their outputs are combined as a weighted sum. The router weights are trained jointly with the rest of the model via gradient descent.

**What is expert collapse and how do you prevent it?**
Expert collapse is when the router learns to send all (or nearly all) tokens to the same 1-2 experts, starving the rest of gradient signal. It is a self-reinforcing failure: experts that receive more tokens improve faster, making the router prefer them more. Prevention requires an auxiliary load-balancing loss added to the training objective. The standard formulation penalizes high variance in per-expert token fraction, typically weighted at alpha=0.01. Monitoring the histogram of tokens per expert during training is essential; any expert consistently above 25-30% of traffic signals incipient collapse.

**Why does a MoE model require much more memory than a dense model of equivalent inference cost?**
All expert weights must reside in GPU memory simultaneously, even though only k of N experts are active per token. A Mixtral 8x7B with 12.9B active parameters requires ~90GB of GPU memory at bfloat16, because all 46.7B parameters must be loaded. By contrast, a dense 12.9B model needs ~25GB. This is the fundamental MoE memory-compute tradeoff: you gain capacity and quality at identical inference FLOPs, but you pay a memory tax proportional to the total-to-active parameter ratio.

**What is expert capacity factor and what happens when it is exceeded?**
Capacity factor defines the maximum number of tokens an expert can process in a single forward pass, expressed as a multiplier over the ideal-uniform load. Capacity = capacity_factor * (total_tokens * k / N). Tokens routed to a full expert are dropped — they skip expert computation and their input hidden state is passed through unchanged (identity fallback). A capacity_factor of 1.25 means each expert can handle 25% more than the uniform ideal. Typical production values: 1.25 for training, 1.5-2.0 for inference to minimize quality degradation.

**Explain expert parallelism. How does it differ from tensor parallelism?**
Expert parallelism distributes different experts across different GPUs; each GPU holds a subset of experts and runs them on routed tokens. Tensor parallelism splits individual weight matrices across GPUs so each GPU holds a shard of every layer. Expert parallelism requires all-to-all communication to route tokens between GPUs; tensor parallelism requires all-reduce. Expert parallelism is more natural for MoE because the split boundary aligns with the expert boundary, but it requires high-bandwidth GPU interconnects for the all-to-all collectives to be efficient.

**How does Mixtral 8x7B achieve 46.7B total parameters with only 12.9B active?**
Mixtral's attention layers are shared and identical to a standard Mistral-7B architecture (~7.7B parameters). Every FFN layer is replaced by a MoE block with 8 expert FFNs each of dimension 14336 (versus a single FFN). 8 experts * 32 layers * (FFN weight matrices) accounts for ~32B additional parameters. With top-2 routing, only 2 of the 8 expert FFNs run per token, so active parameters are roughly 7.7B (attention) + 5.2B (2 experts worth of FFN per layer) = ~12.9B. The other 6 expert FFNs are loaded in memory but idle for any given token.

**What is fine-grained MoE and why does DeepSeek-V3 use it?**
Fine-grained MoE uses many more, smaller experts with a correspondingly higher k. DeepSeek-V3 uses 256 expert FFNs with top-8 routing plus 1 shared expert that always runs. Compared to 8 experts top-2, this provides exponentially more possible expert combinations (256 choose 8 vs 8 choose 2), giving the model far greater flexibility in routing. The shared expert ensures every token has a stable "general knowledge" path regardless of routing. Fine-grained MoE also provides smoother gradient flow across experts since each expert is smaller and more tokens touch each expert per batch.

**What is MoE upcycling and when would you use it?**
Upcycling is converting a trained dense model into a MoE model to reduce MoE training cost. The dense model's FFN weights are copied N times to initialize N expert FFNs (all starting from identical weights), and a randomly initialized gating network is added. Training then continues from this checkpoint. Because you start from a strong initialization rather than random, you need significantly fewer training tokens to reach MoE quality. Use upcycling when you have a good dense model checkpoint and want MoE capacity without the cost of training from scratch.

**How do you serve MoE models efficiently in production?**
Key strategies: (1) Expert parallelism across GPUs with NVLink interconnect to minimize all-to-all latency. (2) Continuous batching to maximize expert utilization — larger batches amortize routing overhead. (3) Expert-aware scheduling: batch requests by predicted expert usage to reduce load imbalance. (4) INT4/INT8 quantization of expert weights to reduce memory, enabling more requests per GPU. (5) Expert offloading to CPU RAM for low-QPS serving (llama.cpp supports this). (6) Monitor dropped token rate and per-expert utilization; adjust capacity factor to production traffic distribution. For Mixtral 8x7B at 1000 req/s, a minimum of 4xA100 80GB is required with tensor+expert parallelism.

**What are the challenges of fine-tuning a MoE model compared to a dense model?**
Three main challenges: (1) All expert weights must be loaded even if you only tune a subset, so memory requirements equal full model inference memory. (2) Applying LoRA to all expert FFNs multiplies adapter count by N, increasing adapter memory and training cost. A common compromise is applying LoRA only to attention layers or to a subset of experts. (3) The routing distribution shifts during fine-tuning — if the fine-tuning domain activates different experts than pretraining, those experts may be undertrained. Best practice: monitor expert utilization on fine-tuning data before training; if certain experts are consistently activated, ensure they are included in the trainable parameter set.

**Compare MoE and dense models when serving a single request at low latency versus a batch of 512 at high throughput.**
At batch size 1, MoE is at a disadvantage. Expert parallelism all-to-all overhead is not amortized, and only k/N experts compute per token. The routing, dispatch, and gather operations add latency without quality gain visible to a single user. Dense models are simpler and faster for single-request serving. At batch size 512, MoE excels. All-to-all communication is amortized over the batch, experts receive enough tokens for efficient GPU utilization, and the model delivers higher quality (more parameters) at the same compute budget. The crossover batch size where MoE becomes favorable depends on interconnect speed — typically batch >= 64-128 for NVLink, higher for InfiniBand.

---

## 13. Best Practices

**Set capacity factor based on production traffic, not training defaults.** Training often uses capacity_factor=1.0 to save memory. Production serving should use 1.25-2.0. Measure dropped token rate on your actual traffic distribution; if drops exceed 1-2%, increase capacity factor or adjust routing.

**Use grouped-query attention (GQA) with MoE.** MoE already increases expert parameter count. Combine with GQA to reduce KV cache memory, leaving more headroom for expert weights. Mixtral uses GQA (32 query heads, 8 KV heads) for this reason.

**Monitor per-expert utilization in production, not just aggregate loss.** Expert utilization can shift as production query distribution drifts from training data. Set alerts for any expert handling >30% or <5% of tokens.

**Prefer NVLink interconnects over InfiniBand for expert parallelism when latency matters.** All-to-all bandwidth is the MoE serving bottleneck. NVLink (600GB/s) versus InfiniBand (200GB/s) can mean 3x lower MoE layer communication latency.

**For LoRA fine-tuning, apply adapters to all expert FFN layers, not only attention.** Expert FFNs contain domain specialization. Fine-tuning only attention with LoRA on a MoE model underperforms fine-tuning the full expert stack, even at small rank.

**Use upcycling when starting a new MoE training run.** If a dense model checkpoint exists at your target active-parameter scale, upcycling saves 30-50% of training compute versus training MoE from scratch.

**Keep alpha (aux loss weight) between 0.001 and 0.01.** Too low and expert collapse occurs; too high and the routing loss dominates the task loss, reducing task quality. Monitor both task loss and expert entropy separately during training.

**For consumer GPU deployment, use GGUF Q4_K_M quantization.** Mixtral 8x7B at Q4_K_M fits in ~26GB, enabling serving on two 16GB GPUs or one 32GB GPU (via llama.cpp expert offloading). Quality degradation versus bfloat16 is modest (MMLU drops ~1-2 points).

**Test with different expert capacity factors offline before production rollout.** Measure quality (downstream task score) versus dropped token rate at capacity_factor {0.75, 1.0, 1.25, 1.5, 2.0}. The knee of the quality-vs-memory curve is typically at 1.25-1.5.

---

## 14. Case Study

### Production Deployment of Mixtral 8x7B at 1000 Requests per Second

#### Problem Statement

A company needs to serve Mixtral 8x7B in production at 1000 requests per second (req/s) with average latency below 800ms for responses up to 512 tokens. Budget constraint: minimize GPU count while meeting latency and throughput SLAs.

#### Architecture Overview

```
  Client Requests (1000 req/s)
           |
  [Load Balancer / API Gateway]
           |
     [LLM Gateway]
     - Request batching (continuous batching)
     - Routing to replica group
     - Dropped token rate monitoring
           |
    -------+-------
    |       |      |
 Replica  Replica  Replica  (3 replicas x 4x A100 80GB per replica)
   |         |       |
   v         v       v
  [vLLM serving process, expert parallelism=4]
  [4x A100 80GB, NVLink interconnect]
  [Mixtral 8x7B in bfloat16, ~90GB across 4 GPUs]
           |
  All-to-All via NVLink for expert dispatch/gather per MoE layer


Expert distribution per replica (4 GPUs):
  GPU-0: Expert {0, 1}   + full attention layers
  GPU-1: Expert {2, 3}   + full attention layers
  GPU-2: Expert {4, 5}   + full attention layers
  GPU-3: Expert {6, 7}   + full attention layers

  Each token: router selects top-2 experts
  All-to-All: tokens go to GPU holding their expert
  Processing: each GPU runs its expert on received tokens
  All-to-All back: combined outputs return to origin GPU
```

#### Key Design Decisions

**GPU selection: A100 80GB over A100 40GB.** 80GB variant fits all 8 experts + KV cache in a single-replica 4-GPU NVLink domain. The 40GB variant requires 8 GPUs and adds InfiniBand hops for cross-node all-to-all, increasing MoE layer latency ~3x.

**Expert parallelism = 4 with NVLink, not tensor parallelism.** Tensor parallelism on Mixtral expert FFNs introduces all-reduce on every expert's output. Expert parallelism aligns naturally with the expert boundary and requires only two all-to-all calls per MoE layer (dispatch + gather). On NVLink (600GB/s), all-to-all for a batch of 512 tokens takes approximately 0.5ms.

**Continuous batching via vLLM.** vLLM's continuous batching saturates expert GPUs without waiting for entire batches to complete. At 1000 req/s with avg 200 input tokens, vLLM achieves effective batch sizes of 400-800 tokens in flight, keeping expert utilization above 70%.

**Capacity factor = 1.5 in production.** Load testing shows that at batch sizes typical in production, ~3% of tokens exceed capacity at factor 1.0. Factor 1.25 drops this to 0.8%. Factor 1.5 drops to 0.1% with acceptable memory increase (each GPU holds ~23GB of expert weights + 3GB buffer = 26GB, well within 80GB).

**INT8 quantization for KV cache only.** Expert FFN weights remain bfloat16 (quantizing experts introduces quality regression more noticeable than KV cache quantization). KV cache in INT8 saves ~15GB per replica at batch size 512, freeing headroom for larger active batches.

**3 replicas for throughput, not expert parallelism.** Each replica serves up to ~350 req/s (limited by MoE layer latency, not GPU compute). 3 replicas comfortably handle 1000 req/s with 15% headroom for traffic spikes. A 4th replica provides N+1 redundancy.

#### Implementation

```python
# vLLM launch command per replica (4x A100 80GB with NVLink)

python -m vllm.entrypoints.openai.api_server \
    --model mistralai/Mixtral-8x7B-Instruct-v0.1 \
    --tensor-parallel-size 4 \       # distributes attention + expert routing
    --max-model-len 8192 \
    --max-num-seqs 512 \             # max concurrent sequences (continuous batching)
    --gpu-memory-utilization 0.90 \  # leave 10% for CUDA kernels
    --quantization None \            # bfloat16 expert weights
    --kv-cache-dtype fp8 \          # INT8 KV cache
    --enable-chunked-prefill \       # better latency for mixed short/long requests
    --max-num-batched-tokens 8192    # max tokens per vLLM scheduler step
```

```python
# Custom monitoring: dropped token rate
# Integrated via vLLM stats callback

from prometheus_client import Gauge, Counter

expert_utilization = Gauge(
    'mixtral_expert_utilization',
    'Fraction of tokens routed to each expert',
    ['expert_id']
)
dropped_tokens_total = Counter(
    'mixtral_dropped_tokens_total',
    'Tokens dropped due to expert capacity overflow'
)

def on_step_end(stats):
    for i, frac in enumerate(stats.expert_token_fractions):
        expert_utilization.labels(expert_id=str(i)).set(frac)
    dropped_tokens_total.inc(stats.dropped_token_count)

# Alert rule: fire if any expert > 35% or dropped_rate > 0.5%
```

```yaml
# Kubernetes HPA for replica scaling
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: mixtral-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: mixtral-serving
  minReplicas: 3
  maxReplicas: 6
  metrics:
  - type: External
    external:
      metric:
        name: vllm_request_queue_depth
      target:
        type: AverageValue
        averageValue: "50"    # scale up when queue > 50 requests per replica
```

#### Capacity Estimation

```
Throughput target:       1000 req/s
Avg input tokens:        200
Avg output tokens:       300
Total tokens/s:          1000 * (200 + 300) = 500,000 tokens/s

Mixtral 8x7B throughput per replica (4x A100, continuous batching):
  Measured: ~18,000 output tokens/s at bfloat16
  Input processing (prefill): ~90,000 tokens/s
  Effective combined: ~15,000 req/s equivalent in tokens

Replicas needed: 500,000 / 15,000 = ~3.3 replicas
Deploy: 4 replicas (3 active + 1 hot standby for N+1)

Total GPUs: 4 replicas * 4 A100 80GB = 16 A100 80GB GPUs
Estimated cost (on-demand H100 equivalent): ~$80/hour
```

#### Tradeoffs and Alternatives

**Alternative: INT4 quantization (AWQ).** Reduces Mixtral 8x7B to ~26GB, fitting on 2x A100 40GB per replica. Halves GPU cost. Quality regression: MMLU drops ~1.5 points, coding tasks drop ~3 points. Acceptable for general chat, not for code or math-heavy workloads.

**Alternative: llama.cpp with expert offloading (low-QPS case).** For <10 req/s, llama.cpp can keep non-active experts in CPU RAM and load them on demand. VRAM requirement drops to ~24GB for active experts + KV cache. Latency increases 2-3x due to CPU-GPU transfers. Not viable for 1000 req/s.

**Alternative: vLLM prefix caching.** For workloads with shared system prompts (e.g., customer support with a fixed 1000-token system prompt), vLLM's prefix caching eliminates reprocessing the shared prefix. Effective throughput improves ~30% for these workloads at no infrastructure cost.

**Monitoring in production:**
- Dropped token rate (alert if > 0.5%)
- Per-expert utilization histogram (alert if any expert > 35% or < 3%)
- All-to-All communication latency per MoE layer (alert if > 2ms)
- GPU memory utilization (alert if > 92%)
- vLLM request queue depth (triggers HPA scaling)

#### Interview Discussion Points

This case study covers: expert parallelism topology decisions (NVLink vs InfiniBand), capacity factor tuning, continuous batching interaction with MoE, quantization tradeoffs specific to MoE (expert weights vs KV cache), replica scaling strategy, and production monitoring for MoE-specific failure modes (expert collapse drift, dropped tokens).
