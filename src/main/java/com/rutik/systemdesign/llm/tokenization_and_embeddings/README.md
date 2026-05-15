# Tokenization & Embeddings

## 1. Concept Overview

Tokenization is the process of converting raw text into discrete units (tokens) that a model can process. These tokens are then mapped to dense vector representations (embeddings) — the actual numerical input to the neural network.

Tokenization sits at the boundary between human-readable text and the mathematical world of neural networks. Poor tokenization design directly impacts model quality: inefficient vocabularies waste model capacity on common subwords; bad handling of numbers, code, or non-Latin scripts degrades performance on those domains.

The key insight of modern tokenization: instead of splitting on words (leads to huge vocabularies with out-of-vocabulary issues) or characters (leads to very long sequences), **subword tokenization** finds a sweet spot — common words are single tokens, rare words split into recognizable pieces.

---

## Intuition

> **One-line analogy**: Tokenization is like breaking a sentence into Lego bricks — common words are single bricks, rare words are split into recognizable pieces — before handing them to the model.

**Mental model**: Think of a dictionary of ~100,000 "subwords" — pieces like "un-", "believ", "-able". Any English text can be expressed as a sequence of these pieces. The model never sees raw characters or full words; it sees these pieces as integer IDs, then looks up a learned vector (embedding) for each. That vector is the model's "understanding" of that piece, learned from context during training.

**Why it matters**: Tokenization determines the effective vocabulary, sequence length, and what the model can efficiently learn. Bad tokenization (e.g., splitting "2024" into ["2", "0", "2", "4"]) makes arithmetic hard. Good tokenization enables multilingual coverage, code understanding, and efficient training.

**Key insight**: The embedding matrix is the interface between discrete symbols (text) and continuous math (neural networks) — everything the model knows about a word is encoded in its embedding vector.

---

## 2. Core Principles

- **Vocabulary**: A fixed set of tokens the model knows (typically 32K–200K for modern LLMs).
- **Subword Units**: Most tokens represent common subwords; rare words decompose into known pieces.
- **No OOV**: Any string of UTF-8 bytes can be represented, even unknown languages (fallback to byte tokens).
- **Deterministic**: The same string always produces the same token sequence (given the same tokenizer).
- **Reversible**: Token IDs can be decoded back to the original text (lossless round-trip).
- **Fertility**: Average tokens-per-word ratio. High fertility (3+ tokens/word) means long sequences, slower inference.

---

## 3. Tokenization Algorithms

### 3.1 Byte Pair Encoding (BPE)

The most widely used algorithm. Starts with individual bytes/characters, iteratively merges the most frequent adjacent pair.

**Training Algorithm:**
```
1. Initialize vocabulary with all bytes/characters
2. For N merge operations:
   a. Count frequency of all adjacent pairs in corpus
   b. Merge the most frequent pair (e.g., "th" appears 50K times)
   c. Add merged unit to vocabulary
   d. Replace all occurrences of the pair in corpus
3. Result: N+initial vocabulary of subword units
```

**Inference (Encoding):**
```
Input: "tokenization"
Step 1: t o k e n i z a t i o n  (split to chars/bytes)
Step 2: Apply learned merges in order:
  "t o" -> "to"... "token" -> "token", "iz" -> "iz", etc.
Output: ["token", "ization"] (2 tokens)
```

Used by: GPT-2, GPT-3, GPT-4 (tiktoken), LLaMA (via SentencePiece BPE), Mistral, Qwen

### 3.2 WordPiece

Similar to BPE but uses likelihood-based merge criterion instead of frequency. Merges the pair that maximizes language model likelihood when merged.

- Subwords prefixed with `##` if they continue a word (e.g., `"tokenization"` → `["token", "##ization"]`)
- The `##` prefix distinguishes word-initial vs. word-internal positions

Used by: BERT, DistilBERT, ELECTRA, most BERT-derived models

### 3.3 SentencePiece (Unigram LM)

Google's tokenization library. Key innovations:
- **Language-agnostic**: Treats the input as a raw byte stream — no need for word boundaries (crucial for CJK languages with no spaces)
- **Unigram LM variant**: Starts with a large vocabulary, iteratively removes tokens that minimally reduce the likelihood of the corpus
- **BPE variant in SentencePiece**: Also supports BPE, now more common

The Unigram model keeps multiple tokenization candidates and picks the highest likelihood one.

Used by: LLaMA (SentencePiece BPE), T5, ALBERT, mT5, PaLM, Gemma

### 3.4 tiktoken (OpenAI)

OpenAI's fast BPE implementation in Rust/Python. Used for GPT-3.5, GPT-4, embedding models.

Key vocabularies:
- `cl100k_base` (100K vocab): GPT-3.5, GPT-4, text-embedding-3
- `o200k_base` (200K vocab): GPT-4o (better multilingual coverage)
- `p50k_base` (50K vocab): GPT-3, Codex

---

## 4. Architecture Diagrams

### Tokenization Pipeline
```
Raw Text: "The quick brown fox"
     |
     v
[Normalization]  -- Unicode normalization, lowercasing (optional)
     |
     v
[Pre-tokenization] -- Split on whitespace/punctuation (language-dependent)
     |   "The" | "quick" | "brown" | "fox"
     v
[BPE/WordPiece/Unigram Encoding]
     |   ["The", "quick", "brown", "fox"]  (common words = 1 token)
     v
[ID Lookup] -- Map tokens to integer IDs
     |   [464, 2068, 7586, 21831]
     v
[Embedding Lookup] -- Map IDs to dense vectors
     |   Shape: [4, d_model]  (4 tokens × embedding dimension)
     v
Transformer Input
```

### Vocabulary Structure
```
Token ID 0-255:    Byte fallback tokens (encode any byte)
Token ID 256-N:    Subword tokens learned by BPE/WordPiece
                   Common: "the", "ing", " of", " the"
                   Medium: "token", "ization"
                   Rare: "antidisestablishmentarianism" (split into ~6 tokens)
Special tokens:    [BOS]=1, [EOS]=2, [PAD]=3, [UNK]=0
```

### Token Embedding Layer
```
Vocabulary Size V (e.g., 32,000)
Embedding Dimension D (e.g., 4,096 for 7B model)

Embedding Matrix W_e: shape [V × D]
  Token ID 5 -> W_e[5] -> 4096-dim vector

Positional Embedding (or RoPE applied inside attention):
  Token at position p -> learned or computed position vector

Input to Transformer = token_embedding + positional_embedding
```

---

## 5. How It Works — Detailed Mechanics

### The Encoding Process (Step by Step)

```python
# Example with tiktoken
import tiktoken
enc = tiktoken.get_encoding("cl100k_base")

text = "Hello, World! 你好"
tokens = enc.encode(text)
# [9906, 11, 4435, 0, 220, 57668, 53901]
# "Hello" -> 9906
# "," -> 11
# " World" -> 4435  (note leading space is part of token)
# "!" -> 0
# " " -> 220
# "你好" -> two tokens [57668, 53901] -- less efficient than Latin text

decoded = enc.decode(tokens)
# "Hello, World! 你好"  -- lossless round-trip
```

### Fertility and Efficiency

**Token density comparison** (approximate tokens per word):
```
English: ~1.3 tokens/word  (most efficient)
French:  ~1.4 tokens/word
Chinese: ~1.5-2 tokens/word (depends on vocab design)
Arabic:  ~2-3 tokens/word
Code:    ~2-4 tokens per identifier (varies with vocabulary)

Implication: An LLM with 4K context window can process:
  ~3000 English words
  ~2000 Arabic words
  ~1500 Python lines of code
```

### Special Tokens

Every model uses special tokens to delimit structure:

| Token | Purpose | Example models |
|-------|---------|---------------|
| `<s>` / `[BOS]` | Beginning of sequence | LLaMA, Mistral |
| `</s>` / `[EOS]` | End of sequence | LLaMA, GPT |
| `[PAD]` | Padding for batching | BERT, T5 |
| `<|system|>`, `<|user|>` | Chat turn delimiters | Qwen, Phi |
| `<|im_start|>` | ChatML format start | OpenAI, many fine-tunes |
| `<|fim_prefix|>` | Fill-in-middle prefix | Code models |
| `[INST]` `[/INST]` | Instruction tags | LLaMA-2-Chat |

### Vocabulary Size Tradeoffs

| Vocab Size | Pros | Cons |
|-----------|------|------|
| Small (8K-16K) | Shorter sequences, smaller embedding matrix | Poor coverage; many multi-token words |
| Medium (32K-50K) | Good English coverage, standard | May struggle with multilingual |
| Large (100K-200K) | Better multilingual, better coding | Larger embedding matrix, rare token quality |

Modern trend: 100K+ vocabulary for broader language coverage (GPT-4o uses 200K, Llama 3 uses 128K).

---

## 6. Real-World Examples

### OpenAI tiktoken Evolution
- GPT-2: 50K vocab (`gpt2` encoding), poor multilingual
- GPT-3/3.5: `p50k_base` 50K, then `cl100k_base` 100K — dramatically better multilingual, 4x fewer tokens for common non-English languages
- GPT-4o: `o200k_base` 200K — further multilingual improvement, better code coverage

### LLaMA Tokenizer
- LLaMA 1/2: SentencePiece BPE, 32K vocabulary
- LLaMA 3: Tiktoken-based BPE, **128K vocabulary** — 4x larger, much better for code, multilingual, math
- Impact: LLaMA 3 generates code with fewer tokens, improving context utilization

### BERT (WordPiece)
- 30K vocabulary, WordPiece encoding
- `[CLS]` token prepended (classification token); `[SEP]` separates sequences
- The `##` prefix is unique to BERT-family models

### Challenges with Numbers and Math
```
Number "12345" with different tokenizers:
  GPT-3.5 (cl100k): ["12345"] -- 1 token (common number)
  GPT-2 (p50k):     ["123", "45"] -- 2 tokens

Complex number "8675309": Varies wildly
  This inconsistency makes arithmetic harder for LLMs

Solutions: Use tokenizers with consistent number splitting
  LLaMA 3 tokenizer: each digit is often its own token
```

---

## 7. Tradeoffs

| Algorithm | Pros | Cons | Best For |
|-----------|------|------|---------|
| BPE | Fast, reproducible, widely used | Not probabilistic; merge order matters | General purpose |
| WordPiece | Likelihood-based; good quality | Slower training | BERT-style models |
| Unigram LM | Probabilistic; multiple segmentations | More complex | Multilingual, T5-style |
| SentencePiece | Language-agnostic, handles any unicode | Extra library dependency | Multilingual models |

---

## 8. When to Use / When NOT to Use Custom Tokenizers

### Use Default Model Tokenizer When:
- Using a pretrained model (API or open-source) — **always use the model's own tokenizer**
- The mismatch between tokenizer and model weights will degrade performance significantly

### Consider Custom Tokenizer When:
- Training a new model from scratch for a specialized domain
- Domain has unusual characters (chemical formulas, music notation, math symbols)
- Your target language has poor coverage in standard vocabularies
- Building a code-specialized model and want single-token identifiers

### Never Do:
- Mix tokenizers from different model families
- Expand vocabulary after pretraining without retraining the embedding layer
- Assume character count ≈ token count when estimating context usage

---

## 9. Common Pitfalls

1. **Token counting != character counting**: "ChatGPT" is 1 token but has 7 characters. Always count tokens with the model's actual tokenizer.
2. **Leading space matters**: `"hello"` and `" hello"` are different tokens in BPE. This matters for prompt construction.
3. **Special token handling**: Forgetting to add BOS/EOS tokens can degrade model performance.
4. **Vocabulary truncation on fine-tuning**: Adding new tokens to a frozen embedding matrix — the new tokens have random embeddings and need extra training.
5. **Non-printing characters**: Prompts with invisible Unicode characters can cause unexpected tokenization.
6. **Byte fallback**: Unknown characters fall back to individual byte tokens (3-4 per UTF-8 character for CJK), dramatically increasing sequence length.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| tiktoken | OpenAI tokenizer | Fast Rust/Python, GPT-4 compatible |
| SentencePiece | Google tokenizer | Used by LLaMA, T5, Gemma |
| HuggingFace tokenizers | Unified interface | Wraps tiktoken, SP, WordPiece; auto-selects from config.json |
| tokenizers (Rust) | HF's fast tokenizer library | Parallelized, used in production |
| spaCy | NLP tokenizer | Not for LLMs; for traditional NLP |
| NLTK | NLP toolkit | Word/sentence tokenization |

```python
# HuggingFace tokenizer usage
from transformers import AutoTokenizer

tokenizer = AutoTokenizer.from_pretrained("meta-llama/Meta-Llama-3-8B")
tokens = tokenizer.encode("Hello, LLM world!", return_tensors="pt")
print(tokenizer.vocab_size)  # 128,256
```

---

## 11. Interview Questions with Answers

**Q: What is Byte Pair Encoding and how does it work?**
A: BPE starts with a character/byte-level vocabulary and iteratively merges the most frequent adjacent pair of tokens until the target vocabulary size is reached. At inference, the same merge operations are applied in order to tokenize new text. This creates a vocabulary that efficiently represents common subwords as single tokens while handling rare words by splitting them into known pieces.

**Q: Why use subword tokenization instead of word-level or character-level?**
A: Word-level creates huge vocabularies (every morphological variant is separate) with out-of-vocabulary issues. Character-level creates very long sequences (100 chars = 100 tokens) and forces the model to learn linguistic patterns from scratch. Subword is the sweet spot: common words are single tokens, rare words decompose into recognizable subwords, and the vocabulary stays manageable.

**Q: What is tokenizer fertility and why does it matter?**
A: Fertility is the average number of tokens per word. High fertility (e.g., Arabic at 3+ tokens/word) means the model processes fewer words within its context window, reducing effective "memory." A model with 4K context but fertility of 3 can only process ~1300 words effectively, versus ~3000 words for English with fertility ~1.3.

**Q: Why does GPT-4 tokenize "hello" differently than "Hello"?**
A: In BPE with case-sensitive vocabularies, " Hello" (with leading space) and "Hello" are different tokens. Additionally, casing changes the token. Leading spaces are merged into the following word token during BPE training, meaning the space is "absorbed" into the token. This is why tokenization is sensitive to capitalization and spacing.

**Q: What happens when an LLM encounters a character it has never seen?**
A: With byte-level BPE (used by GPT models), every possible UTF-8 byte sequence can be represented — there are no truly unknown characters. Characters outside the vocabulary's learned merges fall back to byte-level representation: a Chinese character that isn't a single token gets encoded as 3-4 individual byte tokens, increasing sequence length.

**Q: How do BPE merge rules work and what determines the final vocabulary?**
BPE starts with individual characters and iteratively merges the most frequent adjacent pair into a new token until reaching the target vocabulary size. Each merge creates a new token — for example, if "t" and "h" appear adjacent most often, they merge into "th", then "th" and "e" might merge into "the". The final vocabulary is determined by the merge order and the target vocabulary size (e.g., 32K, 50K, 128K). GPT-2 used 50,257 tokens; LLaMA uses 32,000; GPT-4 uses ~100K. The training corpus determines which merges are learned — training on English-heavy data creates English-efficient tokenization but wastes tokens on other languages.

**Q: What are the tradeoffs of vocabulary size (32K vs 64K vs 128K tokens)?**
Larger vocabularies reduce sequence length (fewer tokens per text) but increase embedding table size and softmax computation. A 32K vocabulary (LLaMA) tokenizes "unfortunately" as one token, while a smaller vocab might split it into 3. However, a 128K vocabulary (GPT-4) adds ~256MB to the embedding layer (128K x 2048 dim x FP16) and makes the softmax output layer 4x more expensive to compute. The optimal size depends on: languages supported (multilingual needs 64K+), domain (code benefits from larger vocab for common patterns), and model size (small models can't leverage huge vocabularies effectively). Mistral's Tekken tokenizer uses 128K for superior multilingual support.

**Q: Why do LLMs show multilingual bias in tokenization and how can it be addressed?**
Multilingual tokenization bias occurs because BPE merge rules are learned from the training corpus, which is typically English-heavy. English text requires ~1.3 tokens per word, while languages like Chinese, Japanese, or Hindi may require 3-5x more tokens per word because their character sequences appear less frequently in the training data. This means non-English users pay more per API call and get shorter effective context windows. Solutions: (1) train tokenizer on balanced multilingual corpus; (2) use larger vocabulary (128K+) to include more non-English tokens; (3) SentencePiece with language-balanced sampling. GPT-4 significantly improved multilingual tokenization compared to GPT-3.5.

**Q: What is the difference between tiktoken and SentencePiece, and when would you choose each?**
tiktoken (OpenAI) is a fast BPE tokenizer optimized for speed (Rust backend), while SentencePiece (Google) is a more flexible framework supporting both BPE and Unigram models. tiktoken is 3-6x faster than SentencePiece for encoding. Choose tiktoken when: building on OpenAI models, need maximum tokenization speed, English-primary workloads. Choose SentencePiece when: training a new model from scratch, need Unigram model support, multilingual focus, or need language-agnostic tokenization (SentencePiece treats text as raw Unicode, no pre-tokenization needed). LLaMA uses SentencePiece; GPT-4 uses tiktoken. For production tokenization of user input, speed often matters most.

**Q: How does the tokenizer affect model performance on code and mathematical expressions?**
Tokenizers can dramatically affect code and math performance because poor tokenization splits meaningful patterns into semantically meaningless pieces. A tokenizer trained primarily on English text might split "def fibonacci(n):" into 6+ tokens, while a code-aware tokenizer keeps "fibonacci" as one token. For mathematics, "3.14159" might be split into ["3", ".", "14", "159"] with a generic tokenizer, destroying the numerical representation. Solutions: (1) include code and math in tokenizer training data; (2) dedicated tokens for common programming constructs (indentation, brackets); (3) digit tokenization strategies — some models tokenize each digit separately for better arithmetic. CodeLLaMA and StarCoder use code-aware tokenizers that significantly improve code completion quality.

---

## 13. Best Practices

1. **Always use the model's own tokenizer** — never mix tokenizers from different model families.
2. **Pre-compute token counts** for long documents before sending to API to avoid exceeding context limits.
3. **Reserve special tokens** when designing new model variants — plan for tool use, formatting, etc.
4. **Test tokenization for your domain** — if building a medical or legal LLM, check that domain-specific terms tokenize efficiently.
5. **Use tiktoken for fast token counting** on GPT-family models, even if not calling the API.
6. **Handle BOS/EOS correctly** — whether your inference code needs to prepend/append them depends on the model's training setup.

---

## 14. Case Study: Tokenizer Design for a Code-Specialized LLM

**Problem:** Building a code-specialized LLM. The base model uses a 32K vocabulary that tokenizes Python identifiers like `self.model.forward()` into 8 tokens. We want to reduce this for efficiency.

**Analysis:**
```
"self.model.forward()" with 32K vocab:
  ["self", ".", "model", ".", "for", "ward", "(", ")"] = 8 tokens

With code-optimized 64K vocab:
  ["self", ".model", ".forward", "()"] = 4 tokens

2x more efficient representation of common code patterns
```

**Approach:**
1. Start with 32K general-purpose BPE vocabulary
2. Train additional BPE merges on 500B tokens of GitHub code
3. Add 32K code-specific tokens (common identifiers, operators, keywords)
4. Fine-tune embedding layer on code while freezing other weights for 10B tokens

**Results:**
- Average fertility on Python: 1.8 tokens/word → 1.1 tokens/word
- Effective context: 4096 tokens → ~7000 code tokens of actual content
- HumanEval pass@1: +8% improvement from better context utilization alone

**Lesson:** Domain-specific tokenizers can yield significant improvements without any architectural changes.
