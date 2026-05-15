# Synthetic Data Generation

## 1. Concept Overview

Synthetic data generation uses LLMs to create training data for other LLMs — a powerful bootstrapping technique that has become central to modern AI development. Rather than relying entirely on human-labeled examples, teams use capable LLMs (teachers) to generate instructions, responses, conversations, and reasoning chains that can train smaller or more specialized models (students).

This field addresses a fundamental bottleneck: human annotation is slow, expensive, and doesn't scale. A single GPT-4 API call can generate a training example in milliseconds for pennies; a human annotator takes minutes and costs dollars. The question is not whether to use synthetic data, but how to generate high-quality data that improves model capabilities.

The LIMA paper (2023) demonstrated a counterintuitive insight: **1000 carefully curated, diverse examples outperformed 52,000 lower-quality examples**. Quality over quantity is the governing principle.

---

## 2. Intuition

> **One-line analogy**: Synthetic data generation is like using an expert professor to write textbooks for a student — cheaper and faster than having the student learn from real-world experience alone.

**Mental model**: Instead of waiting for humans to label 100,000 examples (slow and expensive), you ask a capable LLM (GPT-4, Claude) to generate diverse, high-quality instruction-response pairs. The capable model acts as the "teacher", generating training data. A smaller or cheaper model is then trained on this data as the "student." The quality of what the student can learn is bounded by the quality of what the teacher generates.

**Why it matters**: Synthetic data is how Alpaca, WizardLM, and countless fine-tuned models were created at low cost. It's also how reasoning traces (for math, code) are bootstrapped. Without synthetic data, building specialized models would require massive human labeling budgets.

**Key insight**: Quality beats quantity — 1000 carefully curated, diverse synthetic examples can outperform 52,000 lower-quality ones (LIMA paper). Filtering and quality scoring are as important as generation.

---

## 3. Core Principles

- **Quality over quantity**: A model's capability ceiling is set by its worst training data, not its best.
- **Diversity**: Cover the space of instructions, topics, formats, and difficulty levels.
- **Difficulty calibration**: Mix easy and hard examples; too many hard examples can destabilize training.
- **Deduplication**: Near-duplicate synthetic examples waste capacity and can cause memorization.
- **Verification where possible**: For tasks with ground truth (math, code), verify LLM-generated answers.
- **Human in the loop**: The best datasets use LLMs to draft + humans to curate/filter.

---

## 4. Types / Strategies

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

### 3.7 Preference Data Generation

Generating (chosen, rejected) pairs for DPO, RLHF, and other alignment training methods:

```
Approach 1 — Best-of-N Ranking:
  For each prompt:
    1. Sample 5-10 responses with temperature=0.8
    2. Score each with reward model or LLM-as-judge
    3. Best response = chosen, worst response = rejected
    4. Larger quality gap between chosen/rejected → faster DPO convergence

Approach 2 — Strong vs Weak Model:
  For each prompt:
    1. Generate response with strong model (GPT-4) → chosen
    2. Generate response with weaker model (GPT-3.5) → rejected
    3. Natural quality gap without reward model scoring

Approach 3 — Constitutional AI Revision:
  For each prompt:
    1. Generate initial response → rejected
    2. Critique the response (model self-evaluates)
    3. Revise based on critique → chosen
```

Quality matters more than quantity: 10K high-quality preference pairs with clear quality margins can outperform 100K noisy pairs where chosen and rejected are barely distinguishable. Production pattern: generate 5-10 responses per prompt and use the best/worst for maximum margin.

### 3.8 Backtranslation

Generate input from output — the reverse of normal data generation:

```
Process:
  1. Start with high-quality outputs (expert answers, verified code, documentation)
  2. Prompt LLM: "What question or instruction would produce this answer?"
  3. Pair the generated question with the original expert answer
  4. Quality filter: verify the generated question + original answer are coherent

Example:
  Output (expert): "Use ConcurrentHashMap with computeIfAbsent() for thread-safe lazy init"
  Backtranslated input: "How do I implement thread-safe lazy initialization of cached values in Java?"
  Training pair: (backtranslated input, original expert output)
```

Effective when you have high-quality outputs (expert documentation, verified code solutions) but need diverse prompts to pair them with. Can generate 10-50x more training pairs from a small set of expert outputs.

---

## 5. Architecture Diagrams

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

## 6. How It Works — Detailed Mechanics

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

## 7. Real-World Examples

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

## 8. Tradeoffs

| Method | Quality | Scale | Cost | Verification |
|--------|---------|-------|------|-------------|
| Human annotation | Highest | Low | High | Inherent |
| Self-Instruct (GPT-3) | Medium | High | Low | Hard |
| GPT-4 distillation | High | Medium | Medium | Partial |
| Evol-Instruct | High | High | Medium | Hard |
| Code/Math verified | High | Medium | Medium | Built-in |
| Rejection sampling | High | Low-Medium | Medium | Via reward model |

---

## 9. When to Use / When NOT to Use

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

## 10. Common Pitfalls

1. **Diversity collapse**: Using a single prompt template for all examples → model overfits to template format.
2. **Sycophancy amplification**: LLM teachers tend to generate overly helpful, agreeable responses → trains student to be sycophantic.
3. **Cascade errors**: If teacher model has systematic biases (e.g., always uses Python), student inherits them.
4. **No verification for code**: Generated code that doesn't run makes fine-tuning harmful for code tasks.
5. **Forgetting the base model's knowledge**: Fine-tuning on too much synthetic data for too many epochs can cause catastrophic forgetting.
6. **Instruction-following artifacts**: Models that only see perfectly formatted instructions fail on messy real-world prompts.
7. **Model collapse from recursive synthetic data**: Training on synthetic data generated by a model trained on synthetic data degrades quality across generations. Each generation loses tail distribution diversity — rare but valid outputs become increasingly unlikely. Noticeable degradation appears after 3-5 generations of self-training without fresh human data. Shumailov et al. (2023) showed up to 50% vocabulary diversity loss after 5-10 recursive generations. Mitigation: always mix at least 20-30% real human-generated data, tag synthetic data with lineage tracking, and monitor output diversity metrics (distinct-n, topic entropy) across training generations. The "Habsburg AI" problem — models trained on internet data increasingly polluted with AI-generated content — makes this a growing concern even for pre-training data curation.

---

## 11. Technologies & Tools

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

## 12. Interview Questions with Answers

**Q: What is Self-Instruct and how does it bootstrap instruction data?**
A: Self-Instruct starts with ~175 human-written seed instructions. An LLM generates new instructions by sampling from the existing pool, filters for diversity (ROUGE similarity), then generates responses. This iterative process creates 10K-100K instruction-following examples from a small seed. The key quality issue is that the generated data inherits the teacher model's biases and errors.

**Q: What is the LIMA insight and why does it matter?**
A: LIMA showed that fine-tuning on just 1000 carefully curated, diverse examples produced results comparable to models trained on 52K+ examples. The insight: pre-training gives the model knowledge; instruction tuning just teaches the format of helpful responses. This means teams should invest in data *quality* (carefully select/verify examples) rather than generating massive datasets carelessly.

**Q: What is Evol-Instruct and how is it different from Self-Instruct?**
A: Evol-Instruct takes existing instructions and "evolves" them into more complex variants through operators like adding constraints, deepening, broadening, or concretizing. Unlike Self-Instruct which generates new instructions from scratch, Evol-Instruct systematically increases difficulty. WizardLM used this to create challenging instruction data that trained models to handle harder queries.

**Q: Why is verified synthetic data (for code/math) higher quality than general instruction data?**
A: For code and math, you can execute the generated answer and check if it's correct. This filtering step removes hallucinated solutions — a major problem with LLM-generated data. The resulting training data has near-100% accuracy for verifiable steps, vs. 80-90% for unverified instruction data.

**Q: How do you design a quality filtering pipeline for synthetic training data?**
A quality filtering pipeline uses multiple stages to remove low-quality synthetic samples before they corrupt model training. Stage 1: format validation — check that outputs follow the requested structure (valid JSON, correct section headers). Stage 2: deduplication — MinHash or exact hash to remove duplicates and near-duplicates. Stage 3: difficulty filtering — remove trivially simple or impossibly complex examples using perplexity scoring or model-based difficulty estimation. Stage 4: factual verification — for knowledge-dependent tasks, verify key facts against a reference corpus or use a separate LLM as a fact-checker. Stage 5: human spot-check — sample 1-5% for manual review to calibrate automated filters. Alpaca's original 52K instructions had significant quality issues (~30% contained errors or were low-quality); proper filtering would have reduced this to 15-20K high-quality samples that produced better results, as demonstrated by LIMA (1K curated examples matching 52K unfiltered).

**Q: How does Evol-Instruct work and what are its complexity levels?**
Evol-Instruct (WizardLM) iteratively increases instruction complexity by applying evolution operators to seed instructions. The operators: (1) add constraints — "Write a sort function" becomes "Write a sort function that handles null values and is stable"; (2) deepen — add requirement for explanation or edge case handling; (3) concretize — make abstract instructions specific; (4) increase reasoning steps — require multi-step problem solving; (5) complicate input — add more complex data structures or scenarios. Each evolution step generates a harder instruction from an easier one, creating a natural curriculum. Typically 3-5 evolution rounds are applied. Quality control: after each evolution, verify the instruction is still coherent and answerable. WizardLM-70B trained on Evol-Instruct data outperformed ChatGPT on several benchmarks. The key insight: it is easier to systematically complicate existing instructions than to write complex instructions from scratch.

**Q: What is model collapse and how does training on synthetic data risk causing it?**
Model collapse occurs when a model trained on its own outputs (or outputs from similar models) progressively loses diversity and degrades in quality across generations. Each generation amplifies the biases and errors of the previous one while losing tail distribution coverage — rare but valid outputs become increasingly unlikely. The risk is highest when: (1) synthetic data dominates the training mix (>50%); (2) no human-generated data anchors the distribution; (3) multiple generations of synthetic data are stacked (model A generates data for model B, which generates data for model C). Mitigation: (1) always mix synthetic with human-generated data (at least 30% human); (2) use diverse generator models (not just one model's outputs); (3) explicitly measure output diversity (unique n-grams, topic coverage); (4) include quality filtering that removes outputs too similar to each other. Shumailov et al. (2023) showed that after 5-10 generations of self-training, models can lose up to 50% of their vocabulary diversity.

**Q: What did the LIMA paper demonstrate about data quality vs quantity for fine-tuning?**
LIMA (Less Is More for Alignment) showed that fine-tuning LLaMA 65B on just 1,000 carefully curated instruction-response pairs produced outputs preferred by humans over GPT-4 (DaVinci) in 43% of comparisons, and over Alpaca (52K examples) in 78% of comparisons. The key findings: (1) quality dominates quantity — 1K excellent examples beat 52K mediocre ones; (2) the base pre-trained model already has the knowledge; alignment is about format and style, not teaching new facts; (3) each example should demonstrate a different skill or knowledge type; (4) response style should be consistent (LIMA used a specific helpful, detailed style). Practical implication for synthetic data: invest effort in curating a small, high-quality dataset rather than generating massive amounts of unfiltered synthetic data. The "Superficial Alignment Hypothesis" from LIMA suggests that alignment teaches the model how to present knowledge it already has.

**Q: How do you use a strong model to generate training data for a weaker model without violating terms of service?**
Using a strong model (e.g., GPT-4) to generate training data for a weaker model (knowledge distillation via synthetic data) requires careful attention to terms of service. OpenAI's terms prohibit using GPT-4 outputs to train competing models. Compliant approaches: (1) use models with permissive licenses as teachers — LLaMA (Llama Community License), Mistral (Apache 2.0), or DeepSeek (MIT) have no restrictions on using their outputs for training; (2) use the same provider — OpenAI allows using GPT-4 to fine-tune OpenAI models; (3) generate seed ideas with the strong model but write full responses with human annotators; (4) use the strong model only for evaluation/filtering, not generation. The Vicuna and Alpaca projects used ChatGPT outputs for training (before terms were clarified). Current best practice: use an Apache 2.0 or MIT-licensed model as the teacher, or obtain explicit permission from the API provider.

**Q: How do you measure the diversity and coverage of a synthetic instruction dataset?**
Measuring dataset diversity requires multiple complementary metrics. (1) Instruction diversity — embed all instructions with a sentence embedding model, then measure coverage using k-means clustering (aim for balanced cluster sizes) or compute the average pairwise cosine distance (higher = more diverse). (2) Topic coverage — classify instructions into a taxonomy (reasoning, coding, creative writing, math, etc.) and check for balanced representation. (3) Linguistic diversity — measure vocabulary size, average sentence length distribution, and unique n-gram counts across the dataset. (4) Difficulty distribution — score each instruction by complexity (using perplexity or model-based scoring) and ensure coverage across easy, medium, and hard. (5) Deduplication rate — if >10% of samples are near-duplicates, your generation process lacks diversity. Tools: use embedding clustering with UMAP visualization to spot gaps, and compare your dataset's topic distribution against established benchmarks like FLAN or Open-Instruct.

**Q: How do you generate preference pairs for DPO training using synthetic data?**
The most effective approach is Best-of-N ranking: for each prompt, generate 5-10 responses with temperature=0.8, score them with a reward model or LLM-as-judge, then take the highest-scored response as "chosen" and the lowest as "rejected." The key insight is that margin matters — a large quality gap between chosen and rejected leads to faster and more stable DPO convergence than pairs where both responses are similar quality. An alternative is the strong/weak model approach: generate the chosen response with GPT-4 and the rejected response with GPT-3.5 on the same prompt, creating a natural quality gap without reward model scoring. For Constitutional AI-style generation, the model generates an initial response (rejected), critiques it, then revises based on the critique (chosen). In practice, 10K high-quality preference pairs with clear margins outperform 100K noisy pairs. Always verify that the chosen response is actually better than the rejected — noisy preference labels degrade DPO training significantly.

**Q: What is model collapse and how do you detect it when training on synthetic data?**
Model collapse occurs when a model trained on outputs from itself or similar models progressively loses output diversity and quality across generations. Each generation amplifies biases and errors while losing coverage of rare but valid outputs in the tail of the distribution. Detection relies on monitoring diversity metrics: track distinct-1/distinct-2 (unique unigrams/bigrams divided by total), topic entropy across outputs, and vocabulary size used in generated text. A drop of >10% in any diversity metric between training generations signals collapse onset. Shumailov et al. (2023) demonstrated that after 5-10 recursive self-training generations, models lost up to 50% of vocabulary diversity. The primary mitigation is always mixing synthetic data with real human-generated data — maintain at least 70-80% human data in the training mix. Additional safeguards: use diverse generator models (not just one model's outputs), implement lineage tracking to know which data is synthetic and which generation it came from, and explicitly measure output diversity before and after each training run.

---

## 13. Best Practices

1. **Verify wherever possible** — run generated code, check math answers with symbolic computation.
2. **Use diverse prompt templates** — single template → format overfitting; use 10+ templates for the same task type.
3. **Combine human + synthetic** — human examples define quality ceiling; synthetic provides scale.
4. **Score with reward models** — keep only top-K% of generated examples by reward model score.
5. **Deduplicate semantically** — embed instructions and remove near-duplicates (cosine > 0.95).
6. **Cap per-topic** — if you have 10K examples about sorting algorithms, cap at 100 and diversify.
7. **Evaluate on held-out human examples** — don't evaluate on synthetic data; use human-curated test sets.

---

## 14. Case Study: Building a Customer Support Fine-Tuning Dataset

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
