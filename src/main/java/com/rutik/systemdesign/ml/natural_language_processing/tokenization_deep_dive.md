# Tokenization Deep Dive (BPE, WordPiece, Unigram, SentencePiece)

> This file is a deep-dive sub-file of the [Natural Language Processing](README.md) module.
> It covers subword tokenization algorithms (BPE, WordPiece, Unigram LM), byte-level and
> SentencePiece variants, vocabulary sizing, normalization, and train/inference parity.
> The same tokenizers power modern LLMs — see `llm/tokenization_and_embeddings/` for how
> tokenization interacts with context windows, embeddings, and inference cost.

---

## 1. Concept Overview

Tokenization is the first transformation in every NLP pipeline: it converts a raw string into a sequence of integer IDs that a model can embed. It is also the most underestimated component. The choice of tokenizer fixes the model's vocabulary, determines its out-of-vocabulary (OOV) behavior, controls how many tokens a given text consumes (and therefore compute and cost), and silently encodes assumptions about language, casing, and whitespace.

There are three families:

1. **Word-level**: split on whitespace/punctuation. Simple, but the vocabulary explodes (English has ~1M word forms; morphologically rich languages far more), and any unseen word maps to a single `<UNK>` token, destroying information.
2. **Character-level**: vocabulary is tiny (~100s of symbols) and there is no OOV, but sequences become very long and each token carries little meaning, so models must learn composition from scratch.
3. **Subword-level**: the modern default. Frequent words stay whole ("the", "running"); rare words split into meaningful pieces ("tokenization" -> "token" + "ization"); truly novel strings fall back to characters or bytes. This bounds vocabulary size while eliminating hard OOV.

The dominant subword algorithms are **Byte-Pair Encoding (BPE)**, **WordPiece**, and the **Unigram language model**, usually delivered through the **SentencePiece** or **HuggingFace tokenizers** libraries. Understanding how they differ — and how they fail — is a core senior NLP skill, because a tokenizer mismatch produces silent, hard-to-debug correctness bugs.

---

## 2. Intuition

One-line analogy: a subword tokenizer is a compression codebook for text. Common strings get short codes (one token); rare strings get spelled out from smaller pieces.

Mental model: imagine you must transmit English using a fixed dictionary of 30,000 entries. You would not store every word — you would store common whole words plus reusable fragments ("un-", "-ing", "-tion") so you can reconstruct anything. BPE builds that dictionary by greedily merging the most frequent adjacent pieces; Unigram builds it by starting huge and pruning the least useful pieces.

Why it matters: tokenization sits on the critical path of every request. A tokenizer that needs 1.4 tokens per word ("fertility" 1.4) versus 1.1 makes sequences ~27% longer — directly increasing latency, memory, and (for paid APIs) cost, while shrinking how much real content fits in a fixed context window.

Key insight: there is no "UNK" in a well-built byte-level tokenizer. By falling back to the 256 raw bytes, it can encode any string in any language, emoji, or binary blob — so OOV becomes "more tokens," never "lost information."

---

## 3. Core Principles

1. **Bound the vocabulary, eliminate hard OOV.** Subword units cap the embedding table (typically 30k-100k rows) while guaranteeing every input is representable.
2. **Frequency drives granularity.** Frequent sequences become single tokens; rare ones fragment. This concentrates model capacity where data is.
3. **Tokenization is learned from a corpus, then frozen.** The merge rules / vocabulary are fit once on training data and must be identical at train and inference time. The tokenizer is part of the model artifact.
4. **Normalization happens before segmentation.** Unicode NFKC, optional lowercasing, and whitespace handling change the byte stream the algorithm sees, so they must also be frozen with the tokenizer.
5. **Pieces are not morphemes.** Subword boundaries are statistical, not linguistic. "tokenization" might split as "token"+"ization" or "tok"+"eni"+"zation" depending on corpus frequencies — do not over-interpret them.

---

## 4. Types / Architectures / Strategies

### 4.1 Algorithm comparison

| Algorithm | Build direction | Merge/score criterion | Used by | Tokenizer at inference |
|-----------|-----------------|-----------------------|---------|------------------------|
| BPE | Bottom-up (merge) | Most frequent adjacent pair | GPT-2/3/4, RoBERTa, LLaMA (byte BPE) | Apply learned merges in order |
| WordPiece | Bottom-up (merge) | Pair that maximizes corpus likelihood (`freq(ab) / (freq(a)·freq(b))`) | BERT, DistilBERT, ELECTRA | Greedy longest-match-first |
| Unigram LM | Top-down (prune) | Remove pieces whose removal least hurts corpus likelihood | T5, ALBERT, XLNet, mBART | Viterbi most-likely segmentation |

### 4.2 Pre-tokenization and the whitespace marker

Before subword segmentation, most tokenizers split on whitespace/punctuation (pre-tokenization). Because whitespace itself carries information ("newyork" vs "new york"), tokenizers mark word boundaries:

- WordPiece marks **continuation** with `##`: `playing` -> `play`, `##ing`.
- BPE/SentencePiece mark a **leading space** with `Ġ` (byte-BPE) or `▁` (U+2581, SentencePiece): `Ġplaying` means " playing" with a preceding space.

This is why `"hello"` and `" hello"` can tokenize differently — a frequent source of subtle bugs.

### 4.3 Byte-level vs character-level fallback

- **Character BPE**: unknown characters (rare CJK, emoji) can still hit `<UNK>` if not in the base alphabet.
- **Byte-level BPE** (GPT-2 onward): the base alphabet is the 256 possible bytes, so *every* UTF-8 string is encodable with zero OOV. The cost is that non-ASCII characters consume 2-4 tokens each.

### 4.4 SentencePiece: language-agnostic, raw-stream

SentencePiece treats input as a raw Unicode stream including spaces (encoded as `▁`), with no language-specific pre-tokenization. This makes it ideal for languages without whitespace word boundaries (Chinese, Japanese, Thai) and for fully reversible detokenization. It can train either BPE or Unigram under the hood.

---

## 5. Architecture Diagrams

### BPE training (merge loop)

```
Corpus words (as char sequences, with end-of-word marker </w>):
   l o w </w>        (×5)
   l o w e r </w>    (×2)
   n e w e s t </w>  (×6)
   w i d e s t </w>  (×3)

Step 1: count adjacent pairs -> ("e","s") appears 6+3 = 9 times (most frequent)
        merge -> "es"
Step 2: ("es","t") = 9 -> merge -> "est"
Step 3: ("l","o") = 7 -> merge -> "lo"
        ... continue until vocab_size reached ...

Learned merges (ordered):  es, est, lo, low, ...
Vocabulary = base chars + every merged piece
```

### Applying a tokenizer at inference

```
"lowest"
   |
[normalize: NFKC, (optional) lowercase]
   |
[pre-tokenize: split on whitespace -> ["lowest"]]
   |
[subword segmentation]
   BPE:       apply merges in learned order -> "low" + "est"
   WordPiece: greedy longest prefix in vocab -> "low" + "##est"
   Unigram:   Viterbi best segmentation     -> "low" + "est"
   |
[map pieces -> IDs]  e.g. [3204, 395]
```

---

## 6. How It Works — Detailed Mechanics

### BPE from scratch (training)

```python
from collections import Counter
from typing import Dict, List, Tuple


def get_pair_counts(vocab: Dict[Tuple[str, ...], int]) -> Counter:
    """Count frequency of each adjacent symbol pair across the corpus."""
    pairs: Counter = Counter()
    for symbols, freq in vocab.items():
        for i in range(len(symbols) - 1):
            pairs[(symbols[i], symbols[i + 1])] += freq
    return pairs


def merge_pair(
    vocab: Dict[Tuple[str, ...], int], pair: Tuple[str, str]
) -> Dict[Tuple[str, ...], int]:
    """Replace every occurrence of `pair` with its concatenation."""
    merged: Dict[Tuple[str, ...], int] = {}
    a, b = pair
    for symbols, freq in vocab.items():
        new_symbols: List[str] = []
        i = 0
        while i < len(symbols):
            if i < len(symbols) - 1 and symbols[i] == a and symbols[i + 1] == b:
                new_symbols.append(a + b)
                i += 2
            else:
                new_symbols.append(symbols[i])
                i += 1
        merged[tuple(new_symbols)] = freq
    return merged


def train_bpe(corpus: Dict[str, int], num_merges: int) -> List[Tuple[str, str]]:
    """
    corpus: {word: frequency}. Returns ordered list of learned merges.
    Each word starts as a tuple of characters plus an end-of-word marker.
    """
    vocab: Dict[Tuple[str, ...], int] = {
        tuple(word) + ("</w>",): freq for word, freq in corpus.items()
    }
    merges: List[Tuple[str, str]] = []
    for _ in range(num_merges):
        pairs = get_pair_counts(vocab)
        if not pairs:
            break
        best = max(pairs, key=pairs.get)   # most frequent adjacent pair
        vocab = merge_pair(vocab, best)
        merges.append(best)
    return merges


if __name__ == "__main__":
    corpus = {"low": 5, "lower": 2, "newest": 6, "widest": 3}
    merges = train_bpe(corpus, num_merges=10)
    print(merges)
    # [('e', 's'), ('es', 't'), ('l', 'o'), ('lo', 'w'), ('n', 'e'), ...]
```

### Applying learned BPE merges (encoding)

```python
from typing import List, Tuple


def encode_bpe(word: str, merges: List[Tuple[str, str]]) -> List[str]:
    """Apply merges in the order they were learned (rank = priority)."""
    symbols: List[str] = list(word) + ["</w>"]
    rank = {pair: i for i, pair in enumerate(merges)}

    while True:
        # find the highest-priority (lowest-rank) adjacent pair present
        candidate, best_rank = None, len(merges)
        for i in range(len(symbols) - 1):
            pair = (symbols[i], symbols[i + 1])
            if pair in rank and rank[pair] < best_rank:
                candidate, best_rank = pair, rank[pair]
        if candidate is None:
            break
        # merge every occurrence of the chosen pair
        a, b = candidate
        merged, i = [], 0
        while i < len(symbols):
            if i < len(symbols) - 1 and symbols[i] == a and symbols[i + 1] == b:
                merged.append(a + b)
                i += 2
            else:
                merged.append(symbols[i])
                i += 1
        symbols = merged
    return symbols
```

The critical detail: **BPE applies merges in learned order**, so the merge ranks are part of the artifact. Ship the wrong merge file and identical text produces different tokens.

### WordPiece scoring vs BPE

BPE merges the most *frequent* pair. WordPiece instead merges the pair that most increases corpus likelihood, which is the pair maximizing:

```
score(a, b) = freq(a, b) / (freq(a) * freq(b))
```

This prefers merging pieces that occur together more often than chance would predict, so it favors statistically "bound" pairs over merely frequent ones. At inference, WordPiece does **greedy longest-match-first** from the start of each word, emitting `##` continuations.

### Unigram language model (pruning + Viterbi)

```python
# Conceptual sketch of Unigram LM tokenization (the SentencePiece default).
# Training:
#   1. Seed a large candidate vocabulary (e.g. all substrings up to length k).
#   2. Assign each piece a probability via EM (maximize corpus likelihood).
#   3. Iteratively REMOVE the ~20% of pieces whose deletion least reduces
#      likelihood, until the target vocab size is reached.
# Encoding (inference):
#   Use Viterbi to find the segmentation with the highest product of piece
#   probabilities -> the single most likely split.
#
# Unigram supports "subword regularization": sample alternative segmentations
# during training as data augmentation (improves robustness ~0.5-1 BLEU on MT).
```

### Using a production tokenizer

```python
from transformers import AutoTokenizer

bert = AutoTokenizer.from_pretrained("bert-base-uncased")      # WordPiece
gpt2 = AutoTokenizer.from_pretrained("gpt2")                   # byte-level BPE

text = "Tokenization isn't trivial."
print(bert.tokenize(text))
# ['token', '##ization', 'isn', "'", 't', 'trivial', '.']
print(gpt2.tokenize(text))
# ['Token', 'ization', 'Ġisn', "'t", 'Ġtrivial', '.']   (Ġ marks a leading space)

print(len(gpt2.encode("café")), "tokens for 'café'")
# 'é' is 2 UTF-8 bytes -> byte-level BPE may use 2-3 tokens for one accented char
```

### Training a custom tokenizer

```python
from tokenizers import Tokenizer, models, trainers, pre_tokenizers

def train_custom_bpe(files: list[str], vocab_size: int = 32_000) -> Tokenizer:
    tok = Tokenizer(models.BPE(unk_token="[UNK]"))
    tok.pre_tokenizer = pre_tokenizers.ByteLevel(add_prefix_space=True)
    trainer = trainers.BpeTrainer(
        vocab_size=vocab_size,
        special_tokens=["[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]"],
        min_frequency=2,
    )
    tok.train(files, trainer)
    return tok
```

---

## 7. Real-World Examples

**GPT-2/3/4 (byte-level BPE, `tiktoken`):** ~50k vocabulary over raw bytes, so any string is encodable with no `<UNK>`. The trade-off is visible in pricing: non-English text and code use more tokens per character, so the same content costs more in languages like Thai or Telugu than in English.

**BERT (WordPiece, 30k vocab):** lowercased `bert-base-uncased` splits `"unaffordable"` -> `["una", "##fford", "##able"]`. The `##` continuation markers let the model reconstruct word boundaries.

**LLaMA / T5 (SentencePiece):** treat text as a raw stream with `▁` for spaces, enabling clean multilingual handling and lossless detokenization (no Python-side join heuristics).

**Multilingual fertility gap:** a tokenizer trained mostly on English assigns ~1.1 tokens/word in English but 2-4 tokens/word in Hindi or Burmese. This "tokenization tax" both raises cost and shrinks effective context for under-represented languages — a documented fairness concern, and the reason multilingual models (mBERT, XLM-R, BLOOM) train tokenizers on balanced multilingual corpora.

**Code models (StarCoder, Code Llama):** add tokens for indentation runs and common code patterns; a tokenizer that wastes tokens on `\n    ` (newline + 4 spaces) inflates every Python file by 20-40%.

---

## 8. Tradeoffs

| Dimension | Word-level | Character-level | Subword (BPE/WP/Unigram) |
|-----------|-----------|-----------------|--------------------------|
| Vocab size | Huge (100k-1M+) | Tiny (~100-300) | Bounded (16k-100k) |
| OOV behavior | Hard `<UNK>` | None | None (byte fallback) |
| Sequence length | Short | Very long | Moderate |
| Per-token meaning | High | Very low | Medium-high |
| Morphology | Poor | Implicit | Good |

| Dimension | BPE | WordPiece | Unigram LM |
|-----------|-----|-----------|------------|
| Merge criterion | Frequency | Likelihood ratio | Likelihood-pruning |
| Determinism | Single segmentation | Single (greedy) | Best Viterbi (can sample) |
| Subword regularization | No (native) | No | Yes |
| Multilingual no-whitespace | Needs byte-level | Needs pre-tok | Native (SentencePiece) |

| Vocab size | Sequence length | Embedding table | Notes |
|-----------|-----------------|-----------------|-------|
| 16k | Longer | Small | More fragments, cheaper embeddings |
| 32k | Balanced | Medium | Common default (BERT, LLaMA-1) |
| 100k+ | Shorter | Large | Fewer tokens/word, bigger softmax/embedding cost |

---

## 9. When to Use / When NOT to Use

### Use subword tokenization when

- Training or fine-tuning any transformer (it is effectively mandatory).
- The domain has rich morphology, compounds, or frequent novel strings (chemistry, code, biomedical).
- You need guaranteed coverage of arbitrary input including emoji and mixed scripts (use byte-level).

### Prefer a custom-trained tokenizer when

- Your domain vocabulary is far from the pretrained tokenizer's corpus (e.g. genomic sequences, source code, a non-English language), and fertility on your data is high (>1.6 tokens/word).

### Reuse the pretrained tokenizer (do NOT retrain) when

- You are fine-tuning an existing model — the tokenizer is tied to the learned embeddings; swapping it invalidates them.
- Your data distribution is close to the original corpus; a custom tokenizer adds risk for marginal gains.

### Consider word/character level only when

- Word-level: small closed-vocabulary classical pipelines (TF-IDF already does this).
- Character-level: tasks where spelling is the signal (language ID, some morphological tagging).

---

## 10. Common Pitfalls

### Pitfall 1: Train/inference tokenizer mismatch

```python
# BROKEN: model trained with the uncased WordPiece tokenizer,
# inference accidentally loads the cased one -> token IDs diverge -> garbage output
from transformers import AutoTokenizer
train_tok = AutoTokenizer.from_pretrained("bert-base-uncased")   # training
infer_tok = AutoTokenizer.from_pretrained("bert-base-cased")     # MISMATCH

# FIX: always save the tokenizer with the model checkpoint and load both
# from the same directory.
model.save_pretrained("./artifact")
train_tok.save_pretrained("./artifact")
infer_tok = AutoTokenizer.from_pretrained("./artifact")
```

Production incident pattern: a config typo points inference at a different tokenizer revision. There is no crash — IDs are valid, just wrong — so accuracy silently collapses while every health check passes.

### Pitfall 2: Silent truncation at max length

```python
# BROKEN: a 900-token contract truncated to 512; the operative clause is at the end
enc = tok(long_text, max_length=512, truncation=True)   # tokens 512-900 discarded

# FIX: sliding window with stride (mean/max-pool per-window outputs) or a
# long-context encoder. At minimum, log when truncation fires.
enc = tok(long_text, max_length=512, truncation=True, stride=128,
          return_overflowing_tokens=True)
```

A legal-NLP team shipped a model that ignored ~30% of every long document because the relevant text sat past token 512 — caught only after recall on long inputs cratered.

### Pitfall 3: Leading-space sensitivity in byte-level BPE

```python
# "hello" and " hello" tokenize differently in GPT-2 style tokenizers
gpt2.tokenize("hello")    # ['hello']
gpt2.tokenize(" hello")   # ['Ġhello']
# Concatenating pre-tokenized pieces without preserving spaces shifts every ID.
# FIX: tokenize whole strings, not manually-spliced fragments; use add_prefix_space
# consistently between training and inference.
```

### Pitfall 4: Counting characters instead of tokens for limits/cost

A 280-character tweet is not 280 tokens, and "4 chars per token" is only an English average. For code, JSON, or non-Latin scripts it can be 1-2 chars/token. Always measure with the actual tokenizer (`len(tok.encode(text))`) before enforcing a context or cost budget.

### Pitfall 5: Adding special tokens but forgetting to resize embeddings

```python
# BROKEN: new special tokens get IDs with no embedding rows -> index error / noise
tok.add_special_tokens({"additional_special_tokens": ["[ENTITY]"]})

# FIX: resize the model's embedding table to match the new vocab size
model.resize_token_embeddings(len(tok))
```

---

## 11. Technologies & Tools

| Tool | Use Case | Notes |
|------|----------|-------|
| HuggingFace `tokenizers` | Fast BPE/WordPiece/Unigram training + inference | Rust-backed, ~100x faster than pure Python |
| `transformers.AutoTokenizer` | Load the exact tokenizer for any model | Ties tokenizer to checkpoint |
| SentencePiece | Language-agnostic BPE/Unigram, raw stream | Used by T5, LLaMA, mBART |
| `tiktoken` | OpenAI's byte-level BPE | Fast token counting for GPT models |
| `subword-nmt` | Original BPE implementation (Sennrich 2016) | Reference for MT pipelines |
| spaCy / Moses | Pre-tokenization, detokenization | Word/punct splitting before subword |

---

## 12. Interview Questions with Answers

**Q: Why do modern models use subword tokenization instead of word-level?**
Word-level tokenization forces an enormous vocabulary and still cannot represent unseen words, mapping them to a single `<UNK>` token that destroys information. Subword tokenization caps the vocabulary (typically 30k-100k) while guaranteeing any input is representable by falling back to smaller pieces or bytes. It also shares parameters across morphologically related words ("run", "running", "runner" share the "run" piece), which improves generalization and handles rare/novel strings gracefully.

**Q: Explain BPE training in one paragraph.**
Start with each word as a sequence of characters (plus an end-of-word marker). Count all adjacent symbol pairs across the corpus, merge the single most frequent pair into a new symbol, and repeat. Each merge adds one entry to the vocabulary; the ordered list of merges is the learned model. After `vocab_size - base_alphabet` merges you stop. At inference you re-apply those merges in the same learned order, so frequent sequences collapse into single tokens and rare ones stay fragmented.

**Q: How does WordPiece differ from BPE?**
Both are bottom-up merge algorithms, but they choose merges differently. BPE merges the most *frequent* adjacent pair. WordPiece merges the pair that maximizes corpus likelihood, scoring `freq(a,b) / (freq(a)·freq(b))`, which favors pairs that co-occur more than chance rather than merely frequent ones. At inference WordPiece does greedy longest-match-first segmentation and marks continuation pieces with `##`, whereas BPE replays its ranked merge list.

**Q: How does the Unigram language model tokenizer work, and how is it different?**
Unigram is top-down. It seeds a large candidate vocabulary, assigns each piece a probability via EM to maximize corpus likelihood, then iteratively prunes the pieces whose removal least hurts likelihood until it reaches the target size. At inference it uses Viterbi to pick the single most probable segmentation. Unlike BPE/WordPiece it can also *sample* alternative segmentations (subword regularization), which acts as data augmentation and improves robustness.

**Q: What is byte-level BPE and why is it useful?**
Byte-level BPE (introduced with GPT-2) uses the 256 possible bytes as the base alphabet instead of Unicode characters. Because every UTF-8 string decomposes into bytes, there is no possible OOV — any text, emoji, or even binary is encodable. The cost is that non-ASCII characters span multiple bytes and therefore consume 2-4 tokens each, inflating sequence length for non-English text.

**Q: What is the `##` prefix in BERT's tokens and the `Ġ`/`▁` marker in GPT/SentencePiece?**
They encode word-boundary/whitespace information. WordPiece uses `##` to mark a *continuation* piece (`play`, `##ing` reconstructs "playing"). Byte-level BPE uses `Ġ` to mark a piece that was preceded by a space, and SentencePiece uses `▁` (U+2581) for the same purpose. Without these markers, detokenization could not tell "newyork" from "new york."

**Q: Why can "hello" and " hello" produce different token IDs?**
In byte-level BPE and SentencePiece, the leading space is part of the token. " hello" becomes a single `Ġhello`/`▁hello` token, while "hello" at the start of a string has no leading space and tokenizes as `hello`. This matters when you splice pre-tokenized fragments together: dropping or adding spaces shifts the IDs and can degrade model output.

**Q: How does vocabulary size trade off against sequence length and model size?**
A larger vocabulary means more text fits in each token, so sequences are shorter (less attention compute, which is quadratic in length) — but the embedding and output-softmax tables grow linearly with vocab size, adding parameters and memory. A smaller vocabulary shrinks those tables but fragments text into more tokens, lengthening sequences. Common practice lands at 32k-50k for monolingual and 100k-250k for heavily multilingual models.

**Q: What is tokenizer "fertility" and why does it matter?**
Fertility is the average number of tokens per word (or per character) the tokenizer produces on a given text. High fertility means longer sequences, which increase latency, memory, and API cost while reducing how much real content fits in a fixed context window. A tokenizer trained mostly on English has low fertility on English (~1.1) but high fertility (2-4) on under-represented languages — both a cost and a fairness issue.

**Q: A fine-tuned model gives nonsense in production but worked in evaluation. Tokenization is suspect — how do you debug?**
First confirm the inference tokenizer is byte-for-byte identical to the training one (same name, revision, casing, special tokens) — load both from the saved artifact, not from a hub name. Encode a known sentence with both and diff the IDs. Check normalization settings (lowercasing, NFKC), `add_prefix_space`, and whether special tokens were added after training without `resize_token_embeddings`. A mismatch produces valid-but-wrong IDs, so there is no crash — only silently wrong outputs.

**Q: Should you train a custom tokenizer or reuse a pretrained one?**
Reuse the pretrained tokenizer whenever you fine-tune an existing model — it is bound to the learned embeddings, so replacing it invalidates them. Train a custom tokenizer only when pretraining from scratch or when your domain is far from the original corpus (code, genomics, a non-English language) and measured fertility is high (>1.6 tokens/word). Even then, you must train the model embeddings to match, so a custom tokenizer implies a (re)training budget.

**Q: How do you add domain-specific or special tokens to an existing tokenizer safely?**
Use `add_tokens` / `add_special_tokens`, then immediately call `model.resize_token_embeddings(len(tokenizer))` so the embedding and output layers gain rows for the new IDs. New tokens start with random embeddings, so they need fine-tuning data to become useful. Adding too many rare tokens wastes capacity; reserve this for high-frequency domain markers (e.g. `[ENTITY]`, code keywords).

**Q: Why does the same English sentence cost more tokens in some non-Latin languages?**
The tokenizer's merges were learned mostly from English-heavy data, so it has few multi-character tokens for other scripts and falls back to byte- or character-level pieces. A Hindi or Thai sentence therefore fragments into many more tokens than its English translation. Multilingual models mitigate this by training the tokenizer on balanced multilingual corpora so common pieces exist for many scripts.

**Q: What is subword regularization and which algorithm supports it?**
Subword regularization samples among multiple valid segmentations of the same text during training instead of always using the single best one, acting as data augmentation that makes the model robust to tokenization ambiguity. The Unigram LM (via SentencePiece) supports it natively because it has a probability distribution over segmentations; BPE has a dropout variant (BPE-dropout) that randomly skips merges to achieve a similar effect. Gains are typically ~0.5-1 BLEU on low-resource translation.

**Q: How should you count tokens for a context-window or cost budget?**
Always run the actual model tokenizer: `len(tokenizer.encode(text))`. Character- or word-based estimates ("4 chars per token") are English averages that break badly on code, JSON, math, or non-Latin scripts where the ratio can be 1-2 chars/token. For OpenAI models use `tiktoken` with the model-specific encoding; for HF models use the model's own tokenizer.

**Q: Why is whitespace and newline handling important for code models?**
Code is dense with repeated whitespace (indentation, blank lines). A tokenizer that spends one token per space or per `\n    ` inflates every file by 20-40%, wasting context and compute. Code-oriented tokenizers add tokens for common indentation runs and language patterns, dramatically lowering fertility on source files and improving effective context length.

---

## 13. Best Practices

1. Treat the tokenizer as part of the model artifact: save it with the checkpoint and load both from the same directory. Never reference a bare hub name in production.
2. Pin the tokenizer revision/hash; a silent upstream update can change IDs.
3. Measure fertility on a sample of your real data before fixing context/cost budgets; do not rely on character heuristics.
4. Log (and ideally alarm on) truncation events so silent information loss on long inputs is visible.
5. When fine-tuning, reuse the model's tokenizer unchanged; only add tokens when necessary and always `resize_token_embeddings` afterward.
6. For multilingual or no-whitespace languages, prefer SentencePiece (Unigram) for lossless, language-agnostic handling.
7. Validate train/inference parity with a golden test: a fixed list of strings whose expected token IDs are asserted in CI.
8. For new special tokens, give them descriptive names and verify they survive a save/load round-trip (some fast tokenizers normalize unexpectedly).

---

## 14. Case Study

**Scenario: a domain tokenizer for a Python code-search model.** A team fine-tunes an encoder to embed code snippets for semantic search over an internal monorepo. Using the off-the-shelf `bert-base-uncased` WordPiece tokenizer, they observe fertility of 2.3 tokens/word on code — identifiers like `getUserById` shatter into `get`, `##user`, `##by`, `##id`, and every 4-space indent costs a token. Sequences routinely blow past 512 tokens, truncating function bodies.

They train a custom byte-level BPE tokenizer on 5M lines of internal code.

```python
from tokenizers import Tokenizer, models, trainers, pre_tokenizers, decoders

def train_code_tokenizer(files: list[str], vocab_size: int = 50_000) -> Tokenizer:
    tok = Tokenizer(models.BPE(unk_token="[UNK]"))
    tok.pre_tokenizer = pre_tokenizers.ByteLevel(add_prefix_space=False)
    tok.decoder = decoders.ByteLevel()
    trainer = trainers.BpeTrainer(
        vocab_size=vocab_size,
        special_tokens=["[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]"],
        min_frequency=3,
        # seed the alphabet with indentation runs so they become single tokens
        initial_alphabet=pre_tokenizers.ByteLevel.alphabet(),
    )
    tok.train(files, trainer)
    return tok
```

**Result.** Fertility drops from 2.3 to 1.35 tokens/word on held-out code; the median snippet now fits in 380 tokens instead of 650, eliminating truncation for ~95% of functions. Because the model is trained from the new tokenizer's embeddings, the swap is safe — they did not try to bolt the new tokenizer onto pretrained `bert-base` weights.

**Broken -> fix encountered during the build:**

```python
# BROKEN: they first tried swapping only the tokenizer on a pretrained checkpoint
tokenizer = train_code_tokenizer(files)          # 50k new vocab
model = AutoModel.from_pretrained("bert-base-uncased")   # 30k-row embeddings
# -> token IDs 30000-49999 index out of range; the model never saw these pieces

# FIX: a new tokenizer requires (re)training the embedding table. Either pretrain
# from scratch with the new tokenizer, or resize + continue-pretrain on domain data.
model.resize_token_embeddings(len(tokenizer))
# then run masked-LM continued pretraining on the code corpus before fine-tuning
```

**Validation.** A golden CI test asserts token IDs for 50 canonical snippets so a future tokenizer change cannot silently shift embeddings. Fertility and truncation rate are tracked as dataset metrics, the same way the team tracks label balance.

**Interview discussion points.** Why fertility is the right headline metric for a code tokenizer; why you cannot reuse pretrained embeddings with a new vocabulary; how byte-level fallback guarantees coverage of arbitrary identifiers and Unicode in comments; and how train/inference parity is enforced as a test, not a hope.

---

## See Also

- [Natural Language Processing](README.md) — parent module (preprocessing, embeddings, BERT)
- [bert_and_pretrained_models.md](bert_and_pretrained_models.md) — WordPiece in BERT, fine-tuning, tokenizer/model parity
- [text_representation_and_retrieval.md](text_representation_and_retrieval.md) — how tokenized text feeds BM25 and dense retrieval
- `../../llm/tokenization_and_embeddings/` — tokenization at LLM scale: context windows, embeddings, inference cost
- `../../llm/foundations_and_architecture/` — how token embeddings enter the transformer
