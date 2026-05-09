# Synthetic Data Generation

## 1. Concept Overview

Synthetic data generation uses LLMs to create training data for other LLMs — a powerful bootstrapping technique that has become central to modern AI development. Rather than relying entirely on human-labeled examples, teams use capable LLMs (teachers) to generate instructions, responses, conversations, and reasoning chains that can train smaller or more specialized models (students).

This field addresses a fundamental bottleneck: human annotation is slow, expensive, and doesn't scale. A single GPT-4 API call can generate a training example in milliseconds for pennies; a human annotator takes minutes and costs dollars. The question is not whether to use synthetic data, but how to generate high-quality data that improves model capabilities.

The LIMA paper (2023) demonstrated a counterintuitive insight: **1000 carefully curated, diverse examples outperformed 52,000 lower-quality examples**. Quality over quantity is the governing principle.

---

## Intuition

> **One-line analogy**: Synthetic data generation is like using an expert professor to write textbooks for a student — cheaper and faster than having the student learn from real-world experience alone.

**Mental model**: Instead of waiting for humans to label 100,000 examples (slow and expensive), you ask a capable LLM (GPT-4, Claude) to generate diverse, high-quality instruction-response pairs. The capable model acts as the "teacher", generating training data. A smaller or cheaper model is then trained on this data as the "student." The quality of what the student can learn is bounded by the quality of what the teacher generates.

**Why it matters**: Synthetic data is how Alpaca, WizardLM, and countless fine-tuned models were created at low cost. It's also how reasoning traces (for math, code) are bootstrapped. Without synthetic data, building specialized models would require massive human labeling budgets.

**Key insight**: Quality beats quantity — 1000 carefully curated, diverse synthetic examples can outperform 52,000 lower-quality ones (LIMA paper). Filtering and quality scoring are as important as generation.

---

## 2. Core Principles

- **Quality over quantity**: A model's capability ceiling is set by its worst training data, not its best.
- **Diversity**: Cover the space of instructions, topics, formats, and difficulty levels.
- **Difficulty calibration**: Mix easy and hard examples; too many hard examples can destabilize training.
- **Deduplication**: Near-duplicate synthetic examples waste capacity and can cause memorization.
- **Verification where possible**: For tasks with ground truth (math, code), verify LLM-generated answers.
- **Human in the loop**: The best datasets use LLMs to draft + humans to curate/filter.

---

## 3. Strategies

### 3.1 Self-Instruct (Wang et al. 2022)

Bootstrap instruction data from a small seed set using an LLM. The foundational technique for instruction dataset generation.

```
Algorithm:
1. Start with 175 seed (instruction, response) pairs written by humans
2. For each iteration:
   a. Sample 6 seed instructions
   b. Prompt LLM: "Here are 6 examples. Generate 4 new, diverse instructions"
   c. Filter generated instructions (too similar to existing → discard)
   d. For each new instruction, generate response
   e. Add to instruction pool
3. Repeat until target dataset size reached

Quality filters applied:
  - Remove if ROUGE-L similarity > 0.7 with any existing instruction
  - Remove if instruction starts with unsafe keywords
  - Remove if response is too short (<3 words)
```

Generated the original Alpaca 52K dataset using GPT-3. Later improved by using GPT-4 → **Alpaca-GPT4**.

### 3.2 Evol-Instruct (WizardLM, Xu et al. 2023)

Evolve simple instructions into more complex, challenging ones through iterative rewriting. Addresses the quality problem by specifically targeting harder examples.

```
Evolution operators:
  - Add constraints: "Write a sorting function" → "Write O(n log n) sort using ≤50 lines"
  - Increase complexity: "Explain recursion" → "Explain tail recursion with examples in 3 languages"
  - Deepen: "What is machine learning?" → "Explain backpropagation with partial derivatives"
  - Breadth: "Write a loop" → "Compare for/while/do-while loops with use-cases"
  - Concretize: "Improve code quality" → "Refactor this function to reduce cyclomatic complexity below 5"

Example (1 evolution step):
  Original: "Sort a list in Python"
  Evolved:  "Sort a list of dictionaries by multiple keys in Python, handling None values,
             and explain time complexity"
```

WizardLM trained on Evol-Instruct data significantly outperformed models trained on self-instruct data.

### 3.3 Persona-Driven Generation

Assign diverse personas to guide diverse generation:

```
Personas:
  "You are a PhD student in physics"
  "You are a high school student struggling with algebra"
  "You are a senior software engineer reviewing code"
  "You are a non-native English speaker"

For each persona, generate instructions and responses appropriate to that perspective
Result: naturally diverse vocabulary, complexity, and topic coverage
```

Used in Cosmopedia (HuggingFace) to generate 30B tokens of synthetic educational text.

### 3.4 Multi-Turn Conversation Synthesis

Generate complete multi-turn dialogues:

```
Phase 1: Generate conversation topic + user persona
  Topic: "Setting up a Python development environment"
  User: Beginner programmer, Windows user

Phase 2: Generate opening user message
  User: "How do I install Python on Windows?"

Phase 3: Generate assistant response

Phase 4: Continue turn-by-turn
  User: "Now how do I install packages?"
  Assistant: "Use pip: pip install <package_name>..."
  ...

Phase 5: Quality check: Is conversation coherent? Does assistant maintain role? Are facts correct?
```

### 3.5 Distillation as Synthetic Data

Use a larger model's (teacher's) outputs to train a smaller model (student):

```
Teacher: GPT-4 or Claude 3 Opus
Student: LLaMA 3 8B

For each prompt in your dataset:
  1. Generate teacher response
  2. Store (prompt, teacher_response) pair
  3. Fine-tune student on these pairs (supervised learning)

This is "knowledge distillation" at the data level (not logit-level distillation)
```

Concerns: OpenAI ToS prohibits using outputs to train competing models. Use carefully.

### 3.6 Rejection Sampling / Best-of-N

Generate multiple candidate responses; keep only the best:

```
For each prompt:
  1. Generate N responses (e.g., N=10) with temperature=0.8
  2. Score each with reward model or LLM-as-judge
  3. Keep top-1 or top-K as training data

Variant: Constitutional AI-style self-critique
  1. Generate initial response
  2. Prompt model to critique its own response
  3. Prompt model to revise based on critique
  4. Use revised response as training data
```

---

## 4. Architecture Diagrams

### Self-Instruct Pipeline
```
Seed Instructions (175 human-written)
          |
          v
    [Sample 6 seeds]
          |
          v
    [LLM Generation]
    "Generate 4 new diverse instructions"
          |
          v
    [Similarity Filter] --> discard if ROUGE > 0.7
          |
          v
    [Response Generation]
    LLM generates response for each new instruction
          |
          v
    [Quality Filters]
    - Length check
    - Safety check
    - Format check
          |
          v
    [Add to Pool]
          |
          v  (iterate until target size)
    Final Dataset (10K-100K examples)
```

### Quality Filtering Pipeline
```
Raw Generated Data (1M examples)
          |
          v
[Deduplication]
  MinHash near-dedup, exact dedup
  Remove: 40% of raw data
          |
          v
[Reward Model Scoring]
  Score quality: relevance, accuracy, format
  Keep top 50% by score
          |
          v
[Diversity Sampling]
  Cluster by topic/format
  Ensure even coverage across clusters
          |
          v
[Human Review Sample]
  Spot-check 5% manually
  Adjust filters if needed
          |
          v
Final High-Quality Dataset (~300K examples)
```

---

## 5. How It Works — Detailed Mechanics

### Quality Filtering Methods

**Perplexity filtering**: Score each generated text with a smaller reference model. Very low perplexity = too generic/boring; very high = incoherent. Keep mid-range.

**Reward model scoring**: Train a reward model on human preference data; use it to score generated examples. Keep high-reward examples.

**IFD (Instruction Following Difficulty)**: Score how "hard" an instruction is for the model. Filter to keep appropriately challenging examples.

**Format validation**: Check that structured outputs (JSON, code) are syntactically valid. For code: actually run it and verify output.

**Semantic deduplication**: Embed all instructions; remove those with cosine similarity > threshold (e.g., 0.95).

### Verification for Verifiable Tasks

For math and code, verification is possible and dramatically improves data quality:

```
Math example generation:
  1. Generate problem + solution with GPT-4
  2. Verify by:
     a. Parsing final answer
     b. Running symbolic computation (WolframAlpha, sympy)
     c. Cross-verify with multiple model generations
  3. Keep only verified correct examples

Code example generation:
  1. Generate function + tests with GPT-4
  2. Execute code + tests in sandbox
  3. Keep only passing examples
  4. Optionally: generate multiple solutions, keep cleanest
```

### LIMA Insight

**Less Is More for Alignment** (Zhou et al. 2023): Fine-tune LLaMA with just 1000 carefully curated instructions. Key finding: a model's knowledge comes from pre-training; instruction tuning just teaches the **format and style** of responding. You don't need 52K examples to learn to be helpful — you need 1000 *excellent* examples.

Implication: Spend effort on data quality, not scale. Better to annotate 1000 examples carefully than generate 100K carelessly.

---

## 6. Real-World Examples

### Stanford Alpaca (2023)
- 52K instruction-following examples generated by self-instruct from GPT-3
- Cost: ~$600 for GPT-3 API calls
- Fine-tuned LLaMA 7B to follow instructions
- Proved the concept but had quality issues (hallucinations from GPT-3)

### WizardLM (Microsoft, 2023)
- 250K Evol-Instruct examples starting from Alpaca-52K
- Multiple evolution rounds; each iteration increases complexity
- Outperformed text-davinci-003 on MT-Bench despite being much smaller
- Sparked the "instruction evolution" approach

### Cosmopedia (HuggingFace, 2024)
- 30B tokens of synthetic educational text
- Generated by Mixtral 8x7B with diverse persona prompts
- Topics: textbooks, stories, wiki articles, instructions
- Outperforms most datasets of similar size for knowledge-intensive tasks

### Nemotron-4-340B (NVIDIA, 2024)
- 98% synthetic data for the alignment phase
- Used Nemotron as the teacher model itself (self-improvement)
- Synthetic data quality evaluated by reward models
- Demonstrated that LLMs can generate their own training data at scale

### Phi-1/Phi-2 (Microsoft, 2023)
- "Textbooks Are All You Need" — trained on high-quality synthetic textbook data
- 7B model outperforming much larger models on reasoning tasks
- Data: ~6B tokens, mostly GPT-4-generated educational content
- Proved quality of data >> quantity for small model reasoning

---

## 7. Tradeoffs

| Method | Quality | Scale | Cost | Verification |
|--------|---------|-------|------|-------------|
| Human annotation | Highest | Low | High | Inherent |
| Self-Instruct (GPT-3) | Medium | High | Low | Hard |
| GPT-4 distillation | High | Medium | Medium | Partial |
| Evol-Instruct | High | High | Medium | Hard |
| Code/Math verified | High | Medium | Medium | Built-in |
| Rejection sampling | High | Low-Medium | Medium | Via reward model |

---

## 8. When to Use / When NOT to Use

### Use Synthetic Data Generation When:
- You have <1000 real training examples for a task
- You need diverse coverage of instructions/topics
- Fine-tuning a capable base model (synthetic data works best with good bases)
- Tasks are verifiable (code, math) — highest quality synthetic data

### Be Cautious About:
- Amplifying base model errors: synthetic data from weaker models can reinforce mistakes
- Format vs. knowledge: synthetic data teaches format well but doesn't add knowledge the base model doesn't have
- ToS violations: using commercial API outputs to train competing models
- Evaluation contamination: if your evaluation prompts resemble your synthetic data, results are inflated

---

## 9. Common Pitfalls

1. **Diversity collapse**: Using a single prompt template for all examples → model overfits to template format.
2. **Sycophancy amplification**: LLM teachers tend to generate overly helpful, agreeable responses → trains student to be sycophantic.
3. **Cascade errors**: If teacher model has systematic biases (e.g., always uses Python), student inherits them.
4. **No verification for code**: Generated code that doesn't run makes fine-tuning harmful for code tasks.
5. **Forgetting the base model's knowledge**: Fine-tuning on too much synthetic data for too many epochs can cause catastrophic forgetting.
6. **Instruction-following artifacts**: Models that only see perfectly formatted instructions fail on messy real-world prompts.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Self-Instruct** | Bootstrap instructions | Original Wang et al. code |
| **Alpaca farm** | Evaluation + generation | Stanford; instruction simulation |
| **Evol-Instruct** | Instruction evolution | WizardLM; complexity increase |
| **LLM-Blender** | Ensemble outputs | Select best response across models |
| **Argilla** | Human annotation UI | Label synthetic data; quality filter |
| **Ragas** | RAG data generation | Generate QA pairs from documents |
| **Distilabel** | Synthetic pipeline toolkit | HuggingFace; modular pipeline |
| **OpenAI API** | Teacher model | GPT-4 for high-quality generation |
| **vLLM** | Local inference | Generate at scale from open models |
| **LLaMA-Factory** | Fine-tuning framework | Easy pipeline from data to trained model |

---

## 11. Interview Questions with Answers

**Q: What is Self-Instruct and how does it bootstrap instruction data?**
A: Self-Instruct starts with ~175 human-written seed instructions. An LLM generates new instructions by sampling from the existing pool, filters for diversity (ROUGE similarity), then generates responses. This iterative process creates 10K-100K instruction-following examples from a small seed. The key quality issue is that the generated data inherits the teacher model's biases and errors.

**Q: What is the LIMA insight and why does it matter?**
A: LIMA showed that fine-tuning on just 1000 carefully curated, diverse examples produced results comparable to models trained on 52K+ examples. The insight: pre-training gives the model knowledge; instruction tuning just teaches the format of helpful responses. This means teams should invest in data *quality* (carefully select/verify examples) rather than generating massive datasets carelessly.

**Q: What is Evol-Instruct and how is it different from Self-Instruct?**
A: Evol-Instruct takes existing instructions and "evolves" them into more complex variants through operators like adding constraints, deepening, broadening, or concretizing. Unlike Self-Instruct which generates new instructions from scratch, Evol-Instruct systematically increases difficulty. WizardLM used this to create challenging instruction data that trained models to handle harder queries.

**Q: Why is verified synthetic data (for code/math) higher quality than general instruction data?**
A: For code and math, you can execute the generated answer and check if it's correct. This filtering step removes hallucinated solutions — a major problem with LLM-generated data. The resulting training data has near-100% accuracy for verifiable steps, vs. 80-90% for unverified instruction data.

---

## 12. Best Practices

1. **Verify wherever possible** — run generated code, check math answers with symbolic computation.
2. **Use diverse prompt templates** — single template → format overfitting; use 10+ templates for the same task type.
3. **Combine human + synthetic** — human examples define quality ceiling; synthetic provides scale.
4. **Score with reward models** — keep only top-K% of generated examples by reward model score.
5. **Deduplicate semantically** — embed instructions and remove near-duplicates (cosine > 0.95).
6. **Cap per-topic** — if you have 10K examples about sorting algorithms, cap at 100 and diversify.
7. **Evaluate on held-out human examples** — don't evaluate on synthetic data; use human-curated test sets.

---

## 13. Case Study: Building a Customer Support Fine-Tuning Dataset

**Goal:** Create 50K instruction-following examples for a customer support domain (SaaS product).

**Approach:**

**Phase 1: Seed Data (1K examples)**
- Export 500 real resolved support tickets (anonymized)
- Rewrite into (instruction, response) format manually: 200 human-annotator hours

**Phase 2: Evol-Instruct Expansion (50K → 200K raw)**
- For each of 1K seeds, generate 5 evolved variants
- Operators: more complex issue, multi-step problem, edge case, non-native English user, API-related
- Model: GPT-4, temperature=0.8

**Phase 3: Quality Filtering (200K → 50K)**
- Reward model scoring (trained on 500 human preference pairs): keep top 40%
- Semantic deduplication (cosine > 0.95): remove 15K redundant
- Format validation: remove examples with broken JSON or missing required fields
- Human spot-check: 500 random samples, >90% quality threshold achieved

**Phase 4: Verification for Structured Responses**
- API documentation as ground truth for 30% of examples
- Verify that generated responses don't contradict official docs

**Results:**
- Final dataset: 52K high-quality examples
- Fine-tuned LLaMA 3 8B with LoRA
- Customer satisfaction score: +22% vs. base model
- Resolution rate: 73% vs. 51% for base model (fewer escalations to human agents)
