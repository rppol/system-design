# Instruction Tuning

## 1. Concept Overview

Instruction tuning (also called supervised fine-tuning, SFT) is the process of fine-tuning a base language model on (instruction, response) pairs to teach it to follow natural language instructions. A base language model (e.g., LLaMA-3-8B-Base) predicts the next token given a sequence — it completes text but doesn't follow instructions or answer questions in a user-friendly way. Instruction tuning transforms this into an assistant model that responds helpfully to user requests.

The key insight from the Alpaca paper (Stanford, 2023): ~1,000-50,000 high-quality instruction-response pairs, formatted consistently, are sufficient to produce strong instruction-following behavior from a base model. Data quality dominates data quantity.

---

## Intuition

> **One-line analogy**: Instruction tuning teaches a language model to be a helpful assistant by showing it thousands of examples of good assistant behavior.

**Mental model**: A base language model learns to complete text: given "The Eiffel Tower is located in", it predicts " Paris." It doesn't understand the conversational context of "Tell me where the Eiffel Tower is" → "The Eiffel Tower is located in Paris, France." Instruction tuning shows the model thousands of (question, answer) and (instruction, response) pairs formatted in the exact template it will be used with. The model learns to follow the pattern: "when I see an instruction in this format, respond in this format."

**Why it matters**: Every chat model (GPT, Claude, LLaMA-instruct, Mistral-instruct) is a base model that has been instruction-tuned. Understanding instruction tuning reveals how these models learned their conversational behavior and enables creating custom instruction-following models for specific tasks.

**Key insight**: The format of training data must exactly match the inference-time prompt format — a mismatch here is the most common cause of instruction-tuned model failure.

---

## 2. Core Principles

- **Format alignment is critical**: Training template must match inference template exactly, including special tokens, whitespace, and section headers.
- **Label masking on instruction tokens**: Compute loss only on the response portion; including instruction tokens in the loss teaches the model to recite prompts.
- **Data quality dominates quantity**: 1,000 diverse, high-quality examples often outperforms 100,000 noisy examples.
- **Instruction diversity matters**: Training on a narrow instruction set produces a model that follows only those instruction types.
- **Minimal epochs**: 1-3 epochs; more causes overfitting and memorization of training examples rather than generalizing the instruction-following pattern.

---

## 3. How It Works — Detailed Mechanics

### 3.1 Prompt Templates

Different model families use different instruction templates. The training data must use the exact template for the target model:

```
LLaMA-3 / LLaMA-3-Instruct template:
<|begin_of_text|><|start_header_id|>system<|end_header_id|>
{system_prompt}<|eot_id|><|start_header_id|>user<|end_header_id|>
{user_message}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
{assistant_response}<|eot_id|>

Mistral template:
<s>[INST] {user_message} [/INST] {assistant_response} </s>

ChatML template (OpenAI, Qwen, many others):
<|im_start|>system
{system_prompt}<|im_end|>
<|im_start|>user
{user_message}<|im_end|>
<|im_start|>assistant
{assistant_response}<|im_end|>

Alpaca template (early, now less common):
Below is an instruction that describes a task. Write a response that appropriately completes the request.

### Instruction:
{instruction}

### Response:
{response}
```

Template violations cause immediate, severe quality degradation:
```
Training with LLaMA-3 template + Inference with ChatML template:
  → Model sees unexpected tokens at inference time
  → Responses become incoherent, refuse valid instructions
  → Common cause of "the fine-tuned model is worse" reports
```

### 3.2 Label Masking (Instruction Masking)

Compute cross-entropy loss only on the response tokens; mask the instruction tokens:

```
Example input sequence:
  <|begin_of_text|><|start_header_id|>user<|end_header_id|>
  Translate to French: "Hello, how are you?"<|eot_id|>
  <|start_header_id|>assistant<|end_header_id|>
  Bonjour, comment allez-vous?<|eot_id|>

Without label masking (wrong):
  Loss computed on ALL tokens including instruction
  Model learns: "when I see <|begin_of_text|>, predict header tokens..."
  Teaches model to recite the prompt format; degrades instruction following

With label masking (correct):
  Instruction tokens:   labels = -100 (ignored in cross-entropy loss)
  Response tokens:      labels = token_ids (loss computed on these)
  Loss only computed on: "Bonjour, comment allez-vous?<|eot_id|>"

Implementation:
  input_ids = tokenize(full_sequence)
  labels = copy of input_ids
  labels[:instruction_length] = -100   # mask instruction tokens
  # Train on (input_ids, labels) — loss only on response tokens
```

### 3.3 Data Curation

The quality and diversity of training data is the primary determinant of instruction-tuning success:

**Diversity dimensions**:
```
Task types:
  - Question answering: "What is the capital of France?"
  - Summarization: "Summarize the following article in 3 sentences."
  - Translation: "Translate to Spanish: ..."
  - Code generation: "Write a Python function that..."
  - Analysis: "What are the pros and cons of..."
  - Formatting: "Convert this JSON to a markdown table."
  - Reasoning: "Solve step by step: ..."
  - Roleplay: "You are a customer service agent..."

Response styles:
  - Short factual answers
  - Long explanations
  - Numbered lists
  - Structured JSON/Markdown
  - Multi-turn conversation

Domain coverage:
  - General knowledge
  - Domain-specific (if fine-tuning for a domain)
  - Code/technical
  - Creative
```

**Data quality checklist**:
```
Per-example quality:
  ✓ Response is accurate and factually correct
  ✓ Response directly answers the instruction (not tangential)
  ✓ Appropriate response length (not too brief or verbose)
  ✓ Format matches the instruction (if asked for a list, provide a list)
  ✓ No harmful content, biases, or unsafe responses
  ✓ System prompt matches intended behavior (if present)

Dataset-level quality:
  ✓ Diversity across task types (no single task > 20% of dataset)
  ✓ Diversity of response lengths (mix of short and long)
  ✓ No near-duplicate examples (dedup by instruction similarity)
  ✓ Balanced difficulty (mix of simple and complex)
  ✓ Held-out eval set (10% not seen during training)
```

### 3.4 Training Configuration

```python
from trl import SFTTrainer, SFTConfig
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import LoraConfig

model = AutoModelForCausalLM.from_pretrained("meta-llama/Meta-Llama-3-8B")
tokenizer = AutoTokenizer.from_pretrained("meta-llama/Meta-Llama-3-8B")

# Training configuration
sft_config = SFTConfig(
    max_seq_length=2048,          # truncate examples longer than this
    packing=True,                 # pack short examples into one sequence
    per_device_train_batch_size=4,
    gradient_accumulation_steps=8,  # effective batch = 32
    num_train_epochs=2,           # rarely need more than 3
    learning_rate=2e-4,           # for LoRA; 1e-5 for full FT
    warmup_ratio=0.03,
    lr_scheduler_type="cosine",
    logging_steps=25,
    save_steps=500,
    eval_steps=500,
    bf16=True,
)

# Dataset must be formatted with chat template
def format_example(example):
    return tokenizer.apply_chat_template(
        [
            {"role": "user", "content": example["instruction"]},
            {"role": "assistant", "content": example["response"]}
        ],
        tokenize=False,
        add_generation_prompt=False
    )

dataset = dataset.map(lambda ex: {"text": format_example(ex)})

trainer = SFTTrainer(
    model=model,
    args=sft_config,
    train_dataset=train_dataset,
    eval_dataset=eval_dataset,
    peft_config=LoraConfig(r=16, lora_alpha=32, ...)  # optional LoRA
)

trainer.train()
```

### 3.5 Sequence Packing

Pack short examples into full-length sequences to maximize GPU utilization:

```
Without packing (inefficient):
  Example 1: 200 tokens → padded to 2048 tokens (1848 padding tokens wasted)
  Example 2: 300 tokens → padded to 2048 tokens (1748 padding tokens wasted)
  GPU utilization: ~12% for these two examples

With packing:
  [Example 1: 200 tokens][Example 2: 300 tokens][Example 3: 400 tokens]
  [Example 4: 600 tokens][Example 5: 500 tokens]  = 2000 tokens total
  Padded to 2048: 48 padding tokens
  GPU utilization: ~98%

Implementation:
  SFTConfig(packing=True) in TRL handles this automatically
  Uses ConstantLengthDataset to pack examples into full-length sequences

Caution: packing combines multiple examples in one sequence
  Need attention mask modification to prevent cross-example attention:
    Example 1 tokens should not attend to Example 2 tokens
  SFTTrainer handles this correctly; manual implementations must be careful
```

---

## 4. Architecture Diagram

### Instruction Tuning Data Flow
```
Raw instruction-response pairs
  [(instruction_1, response_1), (instruction_2, response_2), ...]
           |
           v
[Template Application]
  Wrap with model-specific chat template
  e.g., LLaMA-3: <|...|>user<|...|>\n{instruction}<|eot_id|><|...|>assistant<|...|>
           |
           v
[Tokenization]
  Convert to token IDs
  Record instruction vs. response boundaries
           |
           v
[Label Creation]
  labels = input_ids.copy()
  labels[:instruction_end] = -100   (mask instruction)
           |
           v
[Training]
  Cross-entropy loss only on response tokens
  Gradient update via Adam
           |
           v
[Evaluation]
  Generate completions for held-out instructions
  Measure: task accuracy, format compliance, refusal rate
```

### Label Masking Illustration
```
Token sequence:
  T1  T2  T3  T4  T5  T6  T7  T8  T9  T10
  [       INSTRUCTION        ] [  RESPONSE  ]

Input IDs:
  42  87  33  91  15  77  23  68  44  55

Labels (after masking):
  -100 -100 -100 -100 -100  77  23  68  44  55
   ^                   ^     ^
   masked (no loss)          loss computed here
```

---

## 5. Real-World Examples

### Stanford Alpaca (2023)
- Fine-tuned LLaMA 7B on 52,000 instruction-following examples
- Examples generated using GPT-3.5 (self-instruct method)
- Training cost: ~$100; achieved impressive instruction-following quality
- Key lesson: 52K diverse high-quality examples sufficient for base instruction following

### OpenHermes 2.5 (Teknium)
- 1M+ instruction-response pairs curated from diverse sources
- Used for community fine-tuning of LLaMA and Mistral models
- Demonstrates that diversity (1M pairs across many task types) outperforms repetitive domain data

### LLaMA-3-Instruct (Meta, 2024)
- LLaMA-3-8B-Base + multi-round SFT + RLHF
- SFT uses ~10M high-quality instruction pairs covering 30+ task types
- Multi-turn conversation data: 20-30% of training set
- Safety instruction data: explicitly included to produce safe refusals

---

## 6. Tradeoffs

| Dataset Size | Expected Quality | Risk | Best For |
|-------------|-----------------|------|---------|
| 500-1,000 | Good for narrow tasks | Overfitting | Specific single-task |
| 5,000-10,000 | Good general following | Moderate | Domain-specific assistant |
| 50,000-100,000 | Strong diverse following | Low | General assistant |
| 1M+ | Excellent | Very low | Production-grade model |

| Epochs | Quality | Risk |
|--------|---------|------|
| 1 | Underfit on small datasets | Undertrained |
| 2-3 | Optimal for most | Sweet spot |
| 5+ | Memorization, format overfitting | Overtrained |

---

## 7. When to Use / When NOT to Use

### Use Instruction Tuning When:
- Need consistent output format (JSON, markdown, specific response style)
- Need to adapt base model behavior (personality, tone, domain focus)
- Want to add specific skills not in the base model's instruction following
- High-volume production system where a smaller fine-tuned model beats a larger prompted model

### Use Prompting Instead When:
- Task variety is high (can't cover all task types in training data)
- Dataset is too small (<200 examples) to generalize
- Rapid iteration is needed (no training cycle)
- Base model already follows the required instructions reasonably well

---

## 8. Common Pitfalls

**1. Template mismatch between training and inference**
The single most common and damaging mistake: training with Alpaca template but running inference with ChatML template, or vice versa.
Fix: Explicitly verify the template by: loading the tokenizer's `apply_chat_template` with test examples and comparing raw token sequences from training to inference. Add a template validation check to the deployment pipeline.

**2. Missing label masking**
Including instruction tokens in the loss causes the model to learn to "predict" prompts rather than responses. Symptoms: model generates responses that begin with prompt template tokens.
Fix: Verify labels by checking that all instruction tokens have label = -100 before training. Print the first 5 examples' labels alongside input_ids and verify visually.

**3. Too many epochs on small dataset**
5 epochs on 1,000 examples → model memorizes training examples rather than learning the instruction-following pattern. At inference, asks it about training examples → hallucinated memorized responses.
Fix: Evaluate on held-out (20%) data after each epoch. Stop training when eval loss stops decreasing (early stopping). For small datasets, 1-2 epochs is usually enough.

**4. Low instruction diversity**
Dataset with 10,000 examples but all are "summarize this text" → model becomes excellent at summarization but poor at other task types.
Fix: Enforce diversity constraints: no single task type above 15-20% of the dataset. Measure diversity metrics (distinct instruction n-grams) before training.

**5. Imbalanced response lengths**
Training data where 90% of responses are 1-2 sentences → model learns to always respond briefly, even when detailed responses are appropriate.
Fix: Balance response length distribution: include long detailed responses (>500 tokens), medium responses (100-500 tokens), and short responses (<100 tokens) in roughly equal proportions.

**6. Incorrect handling of multi-turn conversations**
Multi-turn conversation data requires correct turn-boundary handling: each turn should mask prior turns appropriately in labels.
Fix: Use `apply_chat_template` with `tokenize=False` to format multi-turn conversations; then re-tokenize and apply masking consistently. TRL's SFTTrainer handles this when the dataset contains properly formatted chat templates.

---

## 9. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **TRL SFTTrainer** | SFT training | Standard tool; handles label masking, packing, multi-turn |
| **Axolotl** | Training orchestration | YAML config; best for custom data formats |
| **LLaMA-Factory** | All-in-one fine-tuning | Web UI; multi-model; easy data format handling |
| **HuggingFace datasets** | Dataset management | Standard format for instruction datasets |
| **OpenHermes 2.5** | Public instruction dataset | 1M+ high-quality examples; good fine-tuning starting point |
| **ShareGPT** | Conversation dataset | Multi-turn conversation format; widely used |
| **Alpaca-Cleaned** | Cleaned instruction dataset | 52K examples from original Alpaca, quality-filtered |
| **WizardLM Evol-Instruct** | High-quality instructions | Evolved/complex instructions for stronger models |

---

## 10. Interview Questions with Answers

**Q: What is instruction tuning and how does it differ from pre-training?**
A: Instruction tuning is supervised fine-tuning on (instruction, response) pairs to teach a model to follow natural language instructions. Pre-training trains on vast unlabeled text corpora (hundreds of billions to trillions of tokens) with a next-token prediction objective — the model learns language and world knowledge but not conversational behavior. Instruction tuning uses a small, curated supervised dataset (thousands to millions of instruction-response pairs) to teach the conversational interface. Pre-training produces a "base model" (text completer); instruction tuning produces an "assistant model" (instruction follower). Scale: pre-training requires months on thousands of GPUs; instruction tuning requires hours on a few GPUs.

**Q: Why is label masking critical in instruction tuning?**
A: Label masking ensures the model only learns to generate responses, not to recite prompts. Without masking, the training loss includes cross-entropy over instruction tokens — the model learns "given the start of a prompt, predict the rest of the prompt." This corrupts the fine-tuning objective. With masking, instruction token labels are set to -100 (ignored by cross-entropy loss), so loss is computed only on response tokens. The model learns the conditional generation: "given an instruction in this format, generate a response in this style." Verifying label masking is correct is one of the first debugging steps when an instruction-tuned model produces poor outputs.

**Q: How do you choose between generating synthetic training data vs. using human-annotated data?**
A: Both have distinct tradeoffs. Human-annotated data: highest quality (experts write genuinely good responses); most expensive ($5-50 per example, depending on complexity); small scale (1K-50K examples is typical). Synthetic data (GPT-4 generated): cheap ($0.01-0.10 per example); scalable to millions; risk of hallucinations, biases, and style artifacts from the teacher model. Best practice: use GPT-4 to generate 50K-500K synthetic examples, then apply quality filtering (remove short, inconsistent, or harmful examples), and supplement with 1K-5K human-annotated examples for quality anchoring. For production models: human examples in the training mix significantly improve real-world quality even at <5% of the dataset.

**Q: What is sequence packing and why does it improve training efficiency?**
A: Sequence packing combines multiple short examples into a single sequence of the model's maximum length (e.g., 2048 tokens). Without packing, each example occupies a full sequence with padding — a 200-token example in a 2048-token sequence wastes 90% of compute on attention over padding tokens. With packing, 5-8 short examples fill one sequence: GPU utilization increases from ~10-30% to 90%+, reducing training time proportionally. Critical requirement: an attention mask must prevent examples from attending to each other — example 1 should not influence example 2 in the same packed sequence. TRL's SFTTrainer with `packing=True` handles this correctly. Warning: attention masks in packed sequences must be carefully validated; incorrect implementation can contaminate learning across examples.

**Q: How do you curate high-quality instruction tuning data?**
A: Data curation follows five steps. First, source diversity: collect instructions from multiple domains and task types; cap any single task type at 15-20% of the total. Second, response quality filtering: use an LLM (GPT-4 or a reward model) to score each (instruction, response) pair and filter out low-scoring examples — typically remove the bottom 20-30%. Third, deduplication: remove near-duplicate instructions using embedding similarity clustering (threshold ~0.9 cosine similarity); duplicates cause overfitting. Fourth, length balance: ensure response length distribution has representatives at all lengths (50-2000 tokens). Fifth, safety review: scan for harmful instructions/responses and remove. For domain-specific fine-tuning: domain expert review of a random sample (100-200 examples) is essential to verify correctness.

**Q: What causes instruction format overfitting?**
A: Instruction format overfitting occurs when the model learns the surface pattern of the training instructions rather than generalizing to new instructions. Symptoms: the model follows training instructions well but struggles with paraphrased or novel instructions; the model "fills in" instruction templates even when not instructed. Causes: (1) Training instructions are too formulaic or all share the same phrasing pattern; (2) Too many epochs cause the model to memorize specific instruction phrasings; (3) Training data covers too few task types, causing the model to map all instructions to the few task types it has seen. Mitigation: high instruction diversity (>100 distinct instruction types), instruction rephrasing augmentation (paraphrase each instruction 2-3 ways), and evaluating on held-out instruction types not seen during training.

**Q: How do you evaluate instruction-tuned models?**
A: Multi-dimensional evaluation: (1) Task accuracy: for verifiable tasks (code execution, math, SQL), run the output through a validator — execution accuracy is ground truth; (2) Format compliance: for structured outputs (JSON, markdown), validate against a schema or check formatting rules; (3) Instruction following rate: for the eval set, what fraction of responses directly address the instruction? (4) LLM-as-judge: use GPT-4 to rate response quality on a scale (1-5) for dimensions like helpfulness, accuracy, format compliance; (5) General capability regression: run the fine-tuned model on a general benchmark (MMLU, MT-Bench) and compare to the base model — fine-tuning should not degrade general capability. Build an eval set before training; never evaluate on training data.

**Q: How does multi-turn conversation data differ from single-turn instruction tuning?**
A: Single-turn data: each training example is one (instruction, response) pair. Multi-turn data: each example is a full conversation with N turns: (user_1, assistant_1, user_2, assistant_2, ..., user_N, assistant_N). For multi-turn training: format all turns in order, apply label masking to all user turns and previous assistant turns — only the last (or each) assistant turn contributes to loss. Multi-turn training teaches the model to maintain conversational context across turns: remembering what was said, building on previous answers, handling follow-up questions. Without multi-turn training data, instruction-tuned models struggle with follow-up questions and context-dependent responses. Include 20-30% multi-turn data in instruction-tuning datasets for production-quality conversational models.

**Q: What is the role of system prompts in instruction tuning?**
A: System prompts are per-session instructions that establish the model's persona, behavioral constraints, and context before the user message. In instruction tuning: include diverse system prompts in the training data so the model learns to respect system prompt constraints. Without system prompt training, the model ignores or inconsistently follows system prompts at inference. Training examples should cover: (1) default helpful assistant persona; (2) domain-specific personas ("You are a medical advisor..."); (3) behavioral constraints ("Always respond in formal English"); (4) format specifications ("Always respond in JSON format"); (5) safety instructions ("Refuse requests for harmful content"). System prompts are part of the chat template and must be handled consistently between training and inference — masked in labels (treated as instruction, not response).

**Q: How do you handle very long instructions or responses in instruction tuning?**
A: Sequences longer than the model's maximum context window must be handled. Options: (1) Truncation: truncate to max_length, but this loses the end of long responses — critical if the main answer is in the truncated portion; (2) Filtering: remove examples where instruction + response exceeds max_length — ensures all training data fits cleanly; (3) Chunking: for long documents in instructions, use document chunking (sliding window over the instruction) to create multiple shorter examples; (4) Long context model: use a model with longer context (LLaMA-3 supports 8K, others 32K+) and increase max_seq_length accordingly. For most instruction tuning: filter examples >2048 tokens (keeps ~85% of typical instruction datasets) and use the model's native context window.

---

## 11. Best Practices

1. **Verify template with actual token IDs** — don't assume a template is correct; print tokenized examples and verify special tokens appear at the right positions.
2. **Validate label masking before training** — inspect the first 5 training examples' labels; confirm instruction tokens are -100 and response tokens have correct IDs.
3. **Cap any single task type at 15-20%** — prevents the model from optimizing one task type at the expense of general instruction following.
4. **Use 2-3 epochs with early stopping** — monitor eval loss; stop when it plateaus; 1-2 epochs is usually sufficient for datasets >10K examples.
5. **Enable packing for efficiency** — use TRL's `packing=True`; reduces GPU compute waste by 5-10× for datasets with short examples.
6. **Maintain a general benchmark suite** — evaluate MMLU or MT-Bench after fine-tuning to verify general capability is not degraded.
7. **Keep a quality-focused eval set separate from training data** — 100-200 manually curated evaluation examples across all task types; this is your ground truth for measuring fine-tuning success.
