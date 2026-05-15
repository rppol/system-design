# Case Study: Design a Real-Time AI Translation System

## Intuition

> **Design intuition**: A real-time translation system is a multi-model routing pipeline with streaming output -- the key design challenges are language detection accuracy on short text, sub-second latency for conversational feel, domain-specific terminology enforcement, and quality estimation without reference translations.

**Key insight for this design**: The choice between specialized NMT models and general-purpose LLMs is not binary. A hybrid routing strategy -- specialized models for high-traffic language pairs (lower latency, lower cost) and LLM fallback for rare/low-resource pairs -- delivers the best combination of quality, latency, and cost. The router that makes this decision is the most critical component in the architecture.

---

## 1. Requirements Clarification

### Functional Requirements
- Translate messages in real time across 50+ language pairs for a messaging platform
- Automatic language detection on input text (including short messages, 1-5 words)
- Streaming partial translations: show word-by-word or phrase-by-phrase output for conversational feel
- Context-aware translation: use conversation history (last 5 turns) for pronoun resolution, consistent terminology, tone matching
- Domain-specific glossaries: enforce translations for brand names, medical terms, legal terms, technical jargon
- Quality estimation: score each translation confidence (0.0-1.0) without reference translations
- Bidirectional translation within a conversation (User A speaks French, User B speaks Japanese)
- Entity preservation: URLs, email addresses, phone numbers, code snippets pass through untranslated
- Formality control: formal/informal register selection per user preference

### Non-Functional Requirements
- **Latency**: P50 < 500ms, P95 < 1s, P99 < 1.5s per sentence (end-to-end)
- **Throughput**: 50,000 translations/second at peak
- **Availability**: 99.95% (communication platform is always-on)
- **Quality**: BLEU > 35 average across all pairs; > 45 for top-20 language pairs
- **Scale**: 200M translations/day; 2M concurrent conversations

### Out of Scope
- Speech-to-text / text-to-speech (assume text input/output)
- Document translation (batch, not real-time)
- User authentication and messaging infrastructure (existing platform)
- Translation memory management UI (admin tooling)

---

## 2. Scale Estimation

### Traffic Estimates
```
Daily translations: 200M
Average message length: 25 tokens (chat messages are short)
Peak concurrent conversations: 2M
Peak translation rate: 50,000 translations/second

Token estimates per translation:
  Input (source text + context): 150 tokens average
    Source sentence: 25 tokens
    Conversation context (last 5 turns): 100 tokens
    Glossary terms + instructions: 25 tokens
  Output (translated text): 30 tokens average
  Total per translation: 180 tokens

Daily token volume:
  200M translations x 180 tokens = 36B tokens/day
  Input: 30B tokens/day; Output: 6B tokens/day

Model split (hybrid routing):
  Top 20 pairs via NMT: 85% of traffic = 170M translations/day
  Remaining pairs via LLM: 15% of traffic = 30M translations/day
```

### Storage Estimates
```
Translation cache:
  Exact-match cache hit rate: ~12% (repeated phrases, greetings)
  Fuzzy-match (semantic) cache hit rate: ~8%
  Total cache hit rate: ~20%
  Effective translations needing model inference: 160M/day

Conversation context store:
  2M concurrent conversations x 5KB context = 10GB in Redis

Glossary storage:
  500 domain glossaries x 10,000 terms x 200 bytes = 1GB
  In-memory for fast lookup

Translation logs (for quality monitoring):
  200M translations x 500 bytes = 100GB/day
  Retention: 30 days = 3TB rolling
```

### Latency Budget
```
End-to-end target: P50 < 500ms

Budget breakdown:
  Language detection:        20ms
  Cache lookup:              5ms
  Glossary lookup:           5ms
  Context assembly:          10ms
  Model routing decision:    5ms
  NMT model inference:       80-150ms (specialized, GPU)
  LLM inference:             300-800ms (for rare pairs)
  Post-processing:           15ms
  Quality estimation:        30ms
  Network overhead:          20ms
  ---
  Total (NMT path):          190ms P50 (well under 500ms)
  Total (LLM path):          410ms P50 (under 500ms target)
  Total (LLM path):          900ms P95 (under 1s target)
```

---

## 3. High-Level Architecture

```
User A (French)                              User B (Japanese)
  |                                                |
  | "Bonjour, pouvez-vous confirmer             | (waiting for
  |  le rendez-vous de demain?"                 |  translation)
  v                                                v
[Messaging Platform WebSocket Gateway]
  |
  v
[Translation Request Router]
  |
  v
[Language Detection Service]
  - fastText model (176 languages, < 5ms)
  - Fallback: user profile language preference
  - Short-text boost: bigram/trigram language priors
  |
  v
[Translation Cache Layer (Redis)]
  - Exact match: hash(source + src_lang + tgt_lang + domain)
  - Fuzzy match: embedding similarity (cosine > 0.95)
  - Hit → return cached translation (skip inference)
  - Miss → continue pipeline
  |
  v
[Context Builder]
  ┌─────────────────────────────────────────────────────┐
  │  1. Fetch conversation history (last 5 turns)       │
  │  2. Lookup domain glossary (if configured)          │
  │  3. Extract entities to preserve (URLs, emails,     │
  │     numbers, code blocks, brand names)              │
  │  4. Detect formality register (user preference)     │
  │  5. Assemble translation prompt / encoder input     │
  └─────────────────────────────────────────────────────┘
  |
  v
[Model Router]
  ┌────────────────────────────────────────────────┐
  │  Decision logic:                               │
  │  IF (src, tgt) in top_20_pairs:                │
  │    → NMT Model Pool (NLLB-3.3B / mBART-50)    │
  │  ELIF (src, tgt) in supported_llm_pairs:       │
  │    → LLM Translation (GPT-4o-mini / Claude)    │
  │  ELSE:                                         │
  │    → Pivot translation via English             │
  │       src → English (NMT) → tgt (NMT or LLM)  │
  └────────────────────────────────────────────────┘
       |                          |
       v                          v
[NMT Inference Cluster]    [LLM Translation Service]
  - NLLB-3.3B on GPU          - GPT-4o-mini / Claude 3.5 Haiku
  - TensorRT optimized         - Streaming SSE responses
  - Batch size: 32             - Context-aware prompting
  - Latency: 80-150ms          - Latency: 300-800ms
       |                          |
       v                          v
[Post-Processing Pipeline]
  ┌──────────────────────────────────────────────┐
  │  1. Entity restoration (reinsert preserved   │
  │     URLs, emails, numbers, code)             │
  │  2. Glossary enforcement (replace terms       │
  │     that deviate from glossary)              │
  │  3. Formatting normalization (punctuation,    │
  │     capitalization, whitespace)              │
  │  4. Script validation (output matches        │
  │     target language script)                  │
  └──────────────────────────────────────────────┘
  |
  v
[Quality Estimation Service]
  - Lightweight QE model (XLM-R based, 30ms)
  - Score: 0.0 - 1.0
  - Threshold: < 0.4 → flag for human review
  - Threshold: 0.4 - 0.7 → show quality indicator to user
  - Threshold: > 0.7 → deliver without warning
  |
  v
[Streaming Delivery]
  - Partial translations via WebSocket
  - Phrase-by-phrase for NMT (beam search segments)
  - Token-by-token for LLM (native streaming)
  - Final quality score appended on completion
  |
  v
User B sees: "Hello, can you confirm
              tomorrow's appointment?"
              [Quality: High confidence]
```

---

## 4. Component Deep Dives

### 4.1 Language Detection

```
Language detection on chat messages is deceptively hard:
  Short text (1-5 words) has very low detection accuracy.
  Multilingual messages ("Let's meet at the cafe, s'il vous plait") confuse detectors.
  Code-switching (mixing languages mid-sentence) is common in multilingual users.

Three-tier detection strategy:

Tier 1: User profile language (0ms, always available)
  Each user sets a primary language in their profile.
  Used as prior probability: P(lang = profile_lang) boosted by 0.3.
  Fallback when text is too short for statistical detection.

Tier 2: fastText language ID (< 5ms, 176 languages)
  Meta's fastText lid.176.bin model.
  Accuracy: 97% on sentences > 20 chars; drops to 72% on < 10 chars.
  Returns top-3 predictions with confidence scores.
  Decision: if top prediction confidence > 0.7 → use it.
            if top prediction confidence 0.4-0.7 → combine with user profile prior.
            if top prediction confidence < 0.4 → fall back to user profile language.

Tier 3: Character script detection (< 1ms, supplement)
  Script-based heuristic for disambiguation:
    CJK characters → Chinese/Japanese/Korean (then use fastText for specifics)
    Cyrillic → Russian/Ukrainian/Bulgarian (fastText disambiguates)
    Arabic script → Arabic/Farsi/Urdu
    Devanagari → Hindi/Marathi/Sanskrit
  Script detection resolves ambiguity when fastText is uncertain.

Edge cases handled:
  Empty message with only emoji → skip translation
  Message is entirely URLs/numbers → skip translation
  Message already in target language → skip translation (save cost)
  Mixed-language message → translate as single unit (model handles it)

Detection accuracy by message length:
  > 50 chars:  98.5%
  20-50 chars: 95.2%
  10-20 chars: 88.7%
  5-10 chars:  79.3%
  < 5 chars:   68.1% (heavily reliant on user profile prior)
```

### 4.2 Translation Model Architecture: NMT vs. LLM

```
The hybrid approach uses two model tiers:

Tier 1: Specialized NMT Models (85% of traffic)
  Model: NLLB-3.3B (Meta's No Language Left Behind)
  Languages: 200+ languages, but quality varies
  Top 20 pairs (by traffic volume):
    EN<->ES, EN<->FR, EN<->DE, EN<->PT, EN<->ZH,
    EN<->JA, EN<->KO, EN<->AR, EN<->RU, EN<->IT,
    EN<->NL, EN<->PL, EN<->TR, EN<->TH, EN<->VI,
    ES<->FR, ES<->PT, ZH<->JA, DE<->FR, AR<->FR

  Deployment:
    8x NVIDIA A10G GPUs (24GB VRAM each)
    TensorRT-LLM optimized: INT8 quantization
    Batch inference: up to 32 sentences per batch
    Latency: 80ms P50, 150ms P95 per sentence
    Throughput: 200 translations/second per GPU
    Total capacity: 1,600 translations/second per cluster
    3 clusters (multi-region): 4,800 translations/second base capacity
    Auto-scaling: up to 10 clusters at peak → 16,000 translations/second

  Advantages:
    5-10x lower latency than LLM
    20-50x lower cost per translation
    BLEU scores 45-55 on high-resource pairs
    No prompt engineering needed
    Deterministic (same input → same output with greedy decoding)

  Limitations:
    No conversation context awareness (sentence-level model)
    Glossary enforcement requires constrained decoding (adds latency)
    Quality drops sharply for low-resource language pairs
    No formality control without fine-tuning separate models

Tier 2: LLM Translation (15% of traffic)
  Model: GPT-4o-mini (primary), Claude 3.5 Haiku (fallback)
  Used for:
    Low-resource language pairs (Swahili, Yoruba, Kazakh, etc.)
    Context-dependent translations (pronoun resolution, tone matching)
    Domain-specific translations requiring glossary adherence
    Formality-controlled output

  Prompt template:
    [System]
    You are a professional translator. Translate the following message
    from {src_lang} to {tgt_lang}.

    CONTEXT (previous conversation for reference):
    {last_5_turns}

    GLOSSARY (must use these exact translations):
    {glossary_terms}

    RULES:
    - Preserve all URLs, emails, phone numbers, and code blocks exactly
    - Use {formality_level} register
    - Maintain consistent terminology with the conversation context
    - Do not add explanations; output only the translation

    [User]
    Translate: "{source_text}"

  Deployment:
    API calls to GPT-4o-mini / Claude 3.5 Haiku
    Streaming enabled for partial translation delivery
    Latency: 300ms P50, 800ms P95
    Cost: ~$0.15/1M input tokens, ~$0.60/1M output tokens (GPT-4o-mini)

  Advantages:
    Context-aware (conversation history in prompt)
    Glossary adherence via instruction following
    Formality control via prompting
    Better quality on low-resource pairs (trained on more diverse data)
    Handles code-switching and mixed-language input gracefully

  Limitations:
    3-10x higher latency than NMT
    20-50x higher cost per translation
    Non-deterministic (same input may produce different output)
    Occasional refusals on edge-case content

Quality comparison (BLEU scores on internal benchmark):
  Language Pair     NLLB-3.3B   GPT-4o-mini   Winner
  EN → ES           52.1        48.3          NMT
  EN → FR           49.8        47.6          NMT
  EN → ZH           41.2        43.7          LLM (context helps)
  EN → JA           38.9        42.1          LLM (context helps)
  EN → AR           36.4        38.9          LLM
  EN → SW           22.1        31.5          LLM (large gap)
  EN → YO           14.3        26.8          LLM (large gap)
  ZH → JA           35.7        40.2          LLM (context helps)

Conclusion: NMT wins on high-resource pairs (speed + quality).
            LLM wins on low-resource pairs and context-dependent translation.
            Hybrid routing captures the best of both.
```

### 4.3 Context Preservation

```
Context preservation is critical for natural-sounding translations in conversations.

Problem examples without context:

  Turn 1: "The doctor said the results are ready."
  Turn 2: "She will call you tomorrow."
  Without context: "She" is ambiguous. Translator might use wrong gender pronoun.
  With context: "She" = the doctor → correct feminine pronoun in gendered languages.

  Turn 1: "I'm looking at the new iPhone model."
  Turn 2: "It's really expensive."
  Without context: "It" could refer to anything.
  With context: "It" = iPhone model → consistent translation.

Context window design:

  For NMT models (no native context support):
    Prepend context as concatenated source sentences separated by <ctx> token:
    Input: "<ctx> Le docteur a dit que les resultats sont prets. <ctx>
            <translate> Elle vous appellera demain."
    Fine-tune NMT on context-augmented parallel corpus.
    Context length: last 3 turns maximum (longer degrades NMT quality).
    Latency increase: ~20ms for context-augmented vs. sentence-only.

  For LLM models (native context support):
    Include last 5 turns in the prompt (both source and translated versions).
    LLM naturally resolves pronouns, maintains tone, handles coreference.
    Context quality: significantly better than NMT approach.

Conversation context store:
  Redis hash per conversation:
    Key: conv:{conversation_id}
    Fields:
      turns: [{src: "...", tgt: "...", src_lang: "fr", tgt_lang: "en", ts: ...}, ...]
      participants: [{user_id, lang, formality_pref}, ...]
      domain: "medical" | "legal" | "general" | ...
      glossary_id: "glossary_medical_en_fr"
    TTL: 2 hours after last message (conversation timeout)

  Memory usage:
    2M concurrent conversations x 5KB = 10GB
    Redis cluster: 3 nodes x 16GB = 48GB (plenty of headroom)

Terminology consistency:
  Problem: NMT model translates "cloud computing" as "informatique en nuage" in turn 1,
           but as "cloud computing" (untranslated loanword) in turn 5.
  Solution: Build per-conversation term memory.
    After each translation, extract key terms and their translations.
    Store: {source_term: "cloud computing", translated_term: "informatique en nuage"}
    On subsequent turns, inject these as glossary constraints.
    This ensures consistent terminology within a conversation even with NMT models.
```

### 4.4 Domain-Specific Glossaries

```
Glossary system design:

Glossary structure:
  {
    glossary_id: "med_en_fr",
    domain: "medical",
    src_lang: "en",
    tgt_lang: "fr",
    terms: [
      {
        source: "myocardial infarction",
        target: "infarctus du myocarde",
        context: "cardiology",
        case_sensitive: false,
        must_translate: true   // force this translation even if model disagrees
      },
      {
        source: "Pfizer",
        target: "Pfizer",       // brand name: keep as-is
        context: "pharma",
        case_sensitive: true,
        must_translate: false   // do-not-translate marker
      }
    ],
    version: 14,
    updated: "2024-11-15"
  }

Glossary enforcement strategies:

  Strategy 1: Prompt injection (LLM path)
    Include glossary terms in the system prompt.
    "GLOSSARY: Always translate 'myocardial infarction' as 'infarctus du myocarde'."
    Compliance rate: ~92% (LLMs sometimes ignore instructions).
    Post-processing check: verify glossary terms appear in output; if not, replace.

  Strategy 2: Constrained decoding (NMT path)
    During beam search, boost probabilities of glossary-matching tokens.
    Implementation: prefix-constrained generation.
    When source contains "myocardial infarction":
      Force decoder to emit tokens for "infarctus du myocarde" at the aligned position.
    Compliance rate: ~99% (hard constraint).
    Latency penalty: +15-30ms per sentence with glossary constraints.

  Strategy 3: Post-processing replacement (both paths)
    Translate normally, then scan output for glossary violations.
    Replace incorrect translations of glossary terms with correct ones.
    Risk: replacement may break grammatical agreement (gender, case, plurality).
    Mitigation: use morphological analyzer to adjust surrounding words.

  Chosen approach: Strategy 2 for NMT, Strategy 1 + Strategy 3 for LLM.

Glossary management:
  500 domain glossaries across medical, legal, finance, tech, gaming.
  Average: 10,000 terms per glossary.
  Storage: Trie data structure for fast prefix matching (O(m) lookup, m = term length).
  In-memory: ~1GB total for all glossaries.
  Update frequency: weekly batch updates; emergency hotfix for critical terms.

  Term extraction pipeline (semi-automated):
    1. Domain expert uploads parallel term list (CSV).
    2. System validates: check for conflicts with existing terms.
    3. Admin approves and publishes new glossary version.
    4. Glossary cache invalidated across all translation nodes within 60 seconds.
```

### 4.5 Streaming Partial Translations

```
Streaming design for real-time conversational feel:

Why streaming matters:
  Without streaming: user sends message → 500ms silence → full translation appears.
  With streaming: user sends message → 100ms → first phrase appears → builds up.
  Perceived latency with streaming: 100-150ms (time to first token).
  Actual improvement: user starts reading while translation completes.

NMT streaming (beam search segments):
  NMT models generate entire output at once (encoder-decoder architecture).
  True token-by-token streaming is not natural for NMT.

  Approach: segment-level streaming.
    1. Split source into segments at clause boundaries (commas, conjunctions).
       "Bonjour, pouvez-vous confirmer le rendez-vous de demain?"
       → Segment 1: "Bonjour,"
       → Segment 2: "pouvez-vous confirmer le rendez-vous de demain?"
    2. Translate segment 1 immediately → stream to client.
    3. Translate segment 2 (with segment 1 as context) → stream to client.
    4. Run post-processing on full assembled translation.

  Latency profile:
    Segment 1 delivered: 100ms (short segment, fast inference)
    Segment 2 delivered: 200ms (longer segment)
    Post-processing + quality score: 250ms total

  Tradeoff: segmented translation can produce slightly lower quality
            than translating the full sentence at once.
            Quality drop: ~1-2 BLEU points on average.
            Acceptable for chat; would not use for document translation.

LLM streaming (native token streaming):
  LLMs naturally stream token by token via SSE.
  Time to first token: 150-250ms.
  Tokens per second: 80-120 tokens/second (GPT-4o-mini).
  Average 30-token translation streams over ~300ms after first token.

  Delivery strategy:
    Buffer tokens until a complete word or phrase boundary.
    Send phrase-by-phrase (not character-by-character) for readability.
    Buffer heuristic: emit when whitespace/punctuation encountered
                      OR buffer exceeds 5 tokens.

WebSocket delivery protocol:
  Client subscribes: ws://translate/stream/{conversation_id}

  Server sends frames:
    {type: "partial", text: "Hello,", progress: 0.3}
    {type: "partial", text: "Hello, can you confirm", progress: 0.7}
    {type: "complete", text: "Hello, can you confirm tomorrow's appointment?",
     quality_score: 0.89, model: "nllb-3.3b"}

  Client renders: progressive text update with typing indicator animation.
```

### 4.6 Quality Estimation

```
Quality estimation (QE) scores translation confidence without reference translations.
This is critical for a production system where you cannot know the "correct" translation.

QE model architecture:
  Base: XLM-RoBERTa-large fine-tuned on QE datasets (MLQE-PE, WMT QE shared tasks).
  Input: source sentence + translated sentence (concatenated with [SEP]).
  Output: scalar score 0.0 - 1.0 (1.0 = perfect translation).
  Inference latency: 30ms on GPU (runs in parallel with post-processing).
  Model size: 560M parameters, INT8 quantized → 600MB VRAM.

Training data:
  WMT QE shared task data: 30K sentence pairs with human quality scores.
  Internal data: 500K translations with human ratings (1-5 scale, normalized to 0-1).
  Augmentation: inject known bad translations (word dropout, random substitution)
                to teach model to detect failures.

Score interpretation and routing:
  Score > 0.7 (High confidence):
    Deliver translation immediately.
    No quality indicator shown to user.
    ~78% of translations fall here.

  Score 0.4 - 0.7 (Medium confidence):
    Deliver translation with subtle quality indicator.
    UI shows: "Machine translated" label (no alarm).
    Log for batch human review (sample 5% for quality monitoring).
    ~17% of translations fall here.

  Score < 0.4 (Low confidence):
    Translation likely has significant errors.
    Options (configurable per deployment):
      Option A: Show translation with warning: "Translation may be inaccurate."
      Option B: Retry with LLM (if originally NMT) and re-score.
      Option C: Route to human translator queue (async, 2-5 min delay).
    ~5% of translations fall here.

  Score < 0.2 (Very low confidence):
    Likely a failure case (wrong language, garbled output, hallucination).
    Do not deliver. Show: "Translation unavailable for this message."
    Alert on-call team if rate exceeds 2% of traffic.
    ~1% of translations fall here.

QE failure modes to detect:
  1. Language mismatch: output is in wrong target language.
     Detection: run language ID on output; mismatch → score = 0.0.
  2. Hallucination: output contains information not in source.
     Detection: QE model catches this as low semantic similarity.
  3. Under-translation: significant source content missing from output.
     Detection: length ratio check (output < 40% of expected length → flag).
  4. Over-translation: model adds content not in source.
     Detection: length ratio check (output > 250% of expected length → flag).
  5. Copy-through: model copies source without translating.
     Detection: character overlap ratio with source > 0.8 → flag.

QE-driven model fallback:
  If NMT translation scores < 0.4:
    Automatically retry with LLM (GPT-4o-mini).
    If LLM score > 0.6: deliver LLM translation.
    If LLM score < 0.6: flag for human review.
  This catches NMT failures on edge cases without routing all traffic through LLM.
  Fallback rate: ~3% of NMT translations need LLM retry.
  Cost impact: negligible (3% of 85% = 2.5% of total traffic).
```

### 4.7 Entity Preservation

```
Entities that must pass through translation unchanged:

Entity types:
  URLs: https://example.com/path?query=value
  Email addresses: user@domain.com
  Phone numbers: +1-555-123-4567
  Code blocks: ```print("hello")```
  Brand names: iPhone, Google Maps, WhatsApp (from glossary)
  Numeric values with units: $49.99, 15kg, 100m
  Dates in standardized format: 2024-03-15, ISO 8601
  User mentions: @username
  Hashtags: #trending

Preservation pipeline:
  1. Pre-processing: scan source text with regex + NER model.
  2. Replace entities with placeholders:
     "Check https://docs.example.com for details"
     → "Check [URL_0] for details"
  3. Translate the placeholder-bearing text.
  4. Post-processing: restore placeholders with original entities.
     "Consultez [URL_0] pour plus de details"
     → "Consultez https://docs.example.com pour plus de details"

  NER model: spaCy multilingual NER (10ms, runs in parallel with language detection).
  Regex patterns: compiled once at startup, < 1ms per message.

Edge cases:
  Entity inside a sentence requiring grammatical adaptation:
    "Email john@acme.com about the meeting"
    → French: "Envoyez un e-mail a john@acme.com a propos de la reunion"
    Placeholder must be positioned correctly in target syntax.

  Numbers with locale formatting:
    Source (EN): "The total is $1,234.56"
    Target (DE): "Die Gesamtsumme betragt 1.234,56 $"
    Number formatting adapts to target locale; currency symbol repositions.

  Dates:
    Source (EN): "Meeting on 03/15/2024" (MM/DD/YYYY)
    Target (DE): "Besprechung am 15.03.2024" (DD.MM.YYYY)
    Date format adapts to target locale conventions.
```

### 4.8 Pivot Translation for Rare Language Pairs

```
Not all language pairs have direct translation models.
50+ languages = 2,500+ possible pairs. Only ~200 pairs have good direct models.

Pivot strategy (via English):
  For pair (Swahili → Thai) with no direct model:
    Step 1: Swahili → English (NLLB or LLM)
    Step 2: English → Thai (NLLB or LLM)

  Latency: 2x single translation = 200-400ms for NMT pivot, 600-1200ms for LLM pivot.
  Quality: typically 3-5 BLEU points lower than direct translation.
           English pivot works well because English is highest-resource language.

  Optimization: pre-compute pivot quality for all pairs.
    If direct model BLEU > pivot BLEU: use direct.
    If pivot BLEU > direct model BLEU: use pivot (surprisingly common for medium-resource pairs).

Pivot language selection:
  English: default pivot for most pairs.
  Chinese: better pivot for CJK pairs (Japanese, Korean).
  Spanish: better pivot for Romance language pairs (Portuguese, Italian, Catalan).
  Arabic: better pivot for Middle Eastern language pairs (Farsi, Urdu).

  Router maintains a pivot preference table:
    (src_lang, tgt_lang) → {direct_model | pivot_via_X | llm_direct}

Multi-hop cost:
  Pivot adds latency and compounds errors.
  Maximum 1 pivot hop allowed. If no path exists with 1 hop → use LLM direct.
  LLM handles rare pairs better than double-pivot through NMT.
```

---

## 5. Caching Strategy

```
Translation caching is critical for cost and latency optimization.

Three-tier cache:

Tier 1: Exact-match cache (Redis, < 5ms)
  Key: SHA256(source_text + src_lang + tgt_lang + domain + formality)
  Value: {translated_text, quality_score, model_used, timestamp}
  TTL: 24 hours (translations are stable for unchanged source)
  Hit rate: ~12% (common greetings, repeated phrases, system messages)
  Size: 50M entries x 500 bytes = 25GB

Tier 2: Fuzzy-match cache (embedding similarity, < 20ms)
  For near-identical inputs: "Hello, how are you?" vs "Hello how are you"
  Implementation:
    Embed source text with multilingual-e5-small (fast, 384 dims).
    Search Redis vector index: cosine similarity > 0.97.
    If match found: return cached translation.
  Hit rate: ~8% additional (catches typo variants, punctuation differences).
  Risk: false positives (two similar but semantically different sentences).
  Mitigation: threshold 0.97 is very conservative; manual review of edge cases.

Tier 3: Translation memory (PostgreSQL, < 30ms)
  For domain-specific repeated translations (medical reports, legal clauses).
  Stores professionally reviewed translations.
  Used only when domain glossary is active.
  Hit rate: ~15% for specialized domains; ~2% for general chat.

Cache invalidation:
  Glossary update → invalidate all cache entries for affected language pair + domain.
  Model update → invalidate all cache entries (new model may produce better translations).
  Manual invalidation: admin can purge specific entries flagged as incorrect.

Cost savings from caching:
  20% cache hit rate on 200M daily translations = 40M cached responses.
  40M x $0.0001 per NMT inference saved = $4,000/day saved.
  40M x $0.0005 per LLM inference saved = $20,000/day saved (if all were LLM).
  Blended savings: ~$6,000/day.
```

---

## 6. Monitoring and Quality Assurance

```
Translation quality cannot be measured automatically with certainty.
A multi-signal monitoring system is required.

Real-time metrics:
  Translation volume: by language pair, model tier, domain
  Latency percentiles: P50, P95, P99 by model tier and language pair
  QE score distribution: monitor for drift (sudden drop = model issue)
  Cache hit rate: by tier, by language pair
  Error rate: failed translations, timeouts, language detection mismatches
  Fallback rate: NMT → LLM fallback frequency (spike = NMT degradation)

Quality monitoring pipeline:
  1. Automated checks (real-time):
     - Language ID on output matches target language (mismatch rate < 0.1%)
     - Length ratio within expected bounds (flag outliers)
     - Entity preservation verified (all placeholders restored)
     - QE score distribution stable (alert if mean drops > 0.05 in 1 hour)

  2. Sampling-based human evaluation (daily):
     - Sample 1,000 translations/day stratified by language pair and QE score
     - Human raters score: adequacy (1-5), fluency (1-5)
     - Track trends: any pair dropping below 3.5 average triggers investigation
     - Cost: $500/day for human evaluation (0.5% of total translation cost)

  3. User feedback loop (continuous):
     - Users can flag bad translations (thumbs down button)
     - Flagged translations reviewed by human translators within 24 hours
     - If confirmed bad: add correct translation to translation memory
     - If false flag: no action (users sometimes flag correct but unfamiliar translations)
     - Flag rate baseline: 0.3% of translations; alert if exceeds 1%

  4. A/B testing framework:
     - Test new models: route 5% of traffic to candidate model
     - Compare QE scores, human evaluation scores, user flag rates
     - Promotion criteria: QE score >= baseline AND flag rate <= baseline
     - Minimum sample: 10,000 translations per language pair before decision

Alerting thresholds:
  P99 latency > 2s for any language pair → page on-call
  QE mean score drops > 0.1 in 30 minutes → page on-call
  Translation error rate > 1% → page on-call
  NMT → LLM fallback rate > 10% → investigate NMT cluster health
  User flag rate > 1.5% for any pair → investigate translation quality
```

---

## 7. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Model architecture | Hybrid NMT + LLM | Single LLM for all | NMT is 5-10x faster and 20-50x cheaper for high-resource pairs; LLM better for rare pairs and context |
| NMT model | NLLB-3.3B | mBART-50, MarianMT | NLLB covers 200+ languages, best quality/size ratio; mBART only 50 languages |
| LLM provider | GPT-4o-mini primary | Claude Haiku, Gemini Flash | Best cost/quality for translation; Claude Haiku as fallback for availability |
| Context window | 5 turns for LLM, 3 turns for NMT | Full conversation | Longer context degrades NMT; 5 turns sufficient for pronoun resolution in LLM |
| Pivot language | English default, language-family pivots | English-only pivot | CJK pairs translate better through Chinese pivot; Romance through Spanish |
| QE model | XLM-RoBERTa fine-tuned | LLM-as-judge | XLM-R is 30ms; LLM-as-judge would add 300ms+ and 10x cost |
| Glossary enforcement | Constrained decoding (NMT) + post-processing (LLM) | Prompt-only for both | Constrained decoding gives 99% compliance vs. 92% prompt-only |
| Streaming | Segment-level (NMT), token-level (LLM) | Wait for complete translation | Streaming reduces perceived latency from 500ms to 100-150ms |
| Cache strategy | Exact + fuzzy + translation memory | Exact-only | Fuzzy matching adds 8% hit rate; translation memory critical for domain use |
| Language detection | fastText + user profile prior + script detection | fastText alone | Short messages (< 10 chars) need user profile prior; 68% → 89% accuracy boost |

---

## 8. Cost Analysis

```
200M translations/day, hybrid routing:

NMT inference costs (85% of traffic = 170M translations/day):
  GPU cluster: 8x A10G GPUs x 3 regions = 24 GPUs
  Cost per A10G: $1.00/hour (on-demand) → $0.40/hour (reserved 1-year)
  24 GPUs x $0.40/hour x 24 hours = $230/day base
  Auto-scaling peak (up to 80 GPUs): add $200/day peak surcharge
  NMT GPU total: ~$430/day

LLM inference costs (15% of traffic = 30M translations/day):
  Input tokens: 30M x 150 tokens = 4.5B tokens/day
  Output tokens: 30M x 30 tokens = 900M tokens/day
  GPT-4o-mini pricing: $0.15/1M input, $0.60/1M output
  Input cost: 4.5B x $0.15/1M = $675/day
  Output cost: 900M x $0.60/1M = $540/day
  LLM total: $1,215/day

NMT-to-LLM fallback (3% of NMT traffic = 5.1M/day):
  Already included in LLM budget above (adds ~$200/day)

Quality estimation model:
  4x A10G GPUs (shared with NMT cluster): $38/day
  Runs on all 200M translations: batched, efficient

Infrastructure:
  Redis cluster (cache + context): 3 nodes x 32GB = $150/day
  PostgreSQL (translation memory, logs): $100/day
  Application servers (16 instances): $200/day
  Load balancers + networking: $50/day
  Monitoring + logging: $80/day
  Infrastructure total: $580/day

Human evaluation:
  1,000 samples/day x $0.50/sample = $500/day

Total daily cost:
  NMT inference:     $430
  LLM inference:     $1,215
  QE model:          $38
  Infrastructure:    $580
  Human evaluation:  $500
  ---
  Total:             $2,763/day = ~$83,000/month

Cost per translation:
  $2,763 / 200M = $0.0000138 = $0.014 per 1,000 translations

Comparison: all-LLM approach:
  200M x 180 tokens x avg $0.30/1M = $10,800/day = 3.9x more expensive
  Plus higher latency on all translations.

Comparison: all-NMT approach:
  Would need 10x more GPUs for rare language pairs (poor quality).
  Fine-tuning NLLB for 2,500 pairs is impractical.
  Quality on low-resource pairs: BLEU 14-22 (unacceptable).
  Hybrid approach is the clear winner.
```

---

## 9. Interview Discussion Points

**Why choose a hybrid NMT + LLM approach instead of using LLMs for everything?** Specialized NMT models like NLLB-3.3B deliver 5-10x lower latency (80ms vs. 400ms) and 20-50x lower cost per translation for high-resource language pairs. On the top 20 pairs that represent 85% of traffic, NMT also produces equal or better BLEU scores. LLMs excel on low-resource pairs (Swahili, Yoruba) where NMT quality drops sharply, and on context-dependent translations requiring pronoun resolution or tone matching. The hybrid router captures the best of both: use NMT where it wins on latency, cost, and quality, and LLM where it wins on coverage and contextual understanding.

**How do you handle language detection failures on very short messages?** Short messages (under 10 characters) drop fastText accuracy from 97% to 68%. The system uses a three-tier approach: fastText statistical detection combined with character script analysis (Cyrillic, CJK, Arabic scripts narrow the candidate set) and a Bayesian prior from the user's profile language setting. The prior boosts detection accuracy on short text from 68% to 89%. For single-word messages where detection confidence remains below 0.4, the system falls back to the user's profile language. This is almost always correct because users rarely switch languages mid-conversation without context.

**What is the hardest part about maintaining consistent terminology across conversation turns?** The core challenge is that NMT models are stateless -- they translate each sentence independently. Without intervention, the same term might be translated differently in turn 1 vs. turn 5. The system maintains a per-conversation term memory that extracts key term pairs after each translation and injects them as glossary constraints on subsequent turns. This adds 15-30ms of latency for constrained decoding but eliminates the jarring experience of inconsistent terminology. For the LLM path, conversation history in the prompt provides natural consistency, but even LLMs can drift over long conversations, so the term memory serves as a hard constraint for both paths.

**How does quality estimation work without reference translations, and what happens when confidence is low?** The QE model is an XLM-RoBERTa-large fine-tuned on 530K sentence pairs with human quality scores. It takes source + translation as input and outputs a 0.0-1.0 confidence score in 30ms. When confidence drops below 0.4 (about 5% of translations), the system has three options depending on configuration: show the translation with a warning label, retry with the LLM if the original was NMT (catches NMT failures on edge cases), or route to an async human translator queue for critical domains like medical or legal. The retry strategy is particularly effective -- about 70% of NMT low-confidence translations score above 0.6 when retried with an LLM, because the LLM handles the ambiguity or context dependency that tripped up the NMT model.

**How do you enforce domain-specific glossary terms in translation output?** Two different strategies depending on the model path. For NMT models, constrained decoding modifies beam search to force glossary-matching tokens at aligned positions, achieving 99% compliance with a 15-30ms latency penalty. For LLMs, glossary terms are injected into the system prompt, achieving about 92% compliance. A post-processing step then scans LLM output for any glossary violations and replaces incorrect translations. The replacement step uses a morphological analyzer to adjust surrounding words for grammatical agreement (gender, case, number) in the target language, preventing grammatically broken sentences from simple find-and-replace.

**What is the streaming strategy difference between NMT and LLM, and why does it matter for user experience?** LLMs naturally produce tokens one at a time, so streaming is straightforward -- buffer until a phrase boundary and emit. NMT models produce the entire output at once via encoder-decoder architecture. The system works around this by splitting the source into clause-level segments, translating each segment independently, and streaming segment-by-segment. This reduces perceived latency from 500ms (wait for full translation) to 100-150ms (first segment appears). The tradeoff is a 1-2 BLEU point quality reduction from segmented translation, which is acceptable for chat but would not be used for document translation where quality matters more than speed.

**How would you handle a sudden spike in traffic for a previously low-traffic language pair?** The model router maintains traffic statistics per language pair. If a pair normally handled by the LLM tier suddenly spikes (e.g., a geopolitical event causes surge in Ukrainian-English traffic), the system first scales LLM concurrency via additional API capacity. For sustained spikes, the team can fine-tune and deploy a dedicated NMT model for that pair within 2-3 days using NLLB as a base model and publicly available parallel corpora. The caching layer absorbs some of the spike -- repeated phrases from news articles or common reactions hit the exact-match cache. Auto-scaling policies for the NMT GPU cluster handle demand changes within the existing supported pairs.

**What is the pivot translation strategy and when does it fail?** For language pairs without a direct model (e.g., Swahili to Thai), the system translates through a pivot language: Swahili to English, then English to Thai. English is the default pivot because it has the highest-resource training data. However, for CJK language pairs, Chinese is a better pivot because CJK languages share cultural context and writing system roots. The main failure mode is error compounding -- each translation step introduces errors, and two steps compound them, typically losing 3-5 BLEU points versus direct translation. The system limits pivot to a maximum of one hop. If no single-hop path produces acceptable quality (QE score above 0.5), it falls back to LLM direct translation, which handles rare pairs without pivoting.

**How do you prevent translation of entities like URLs, code blocks, and brand names?** A pre-processing step scans the source text with compiled regex patterns and a multilingual NER model (spaCy, 10ms). Detected entities are replaced with indexed placeholders ([URL_0], [EMAIL_0], [CODE_0]) before translation. After translation, placeholders are restored with the original entities. The tricky part is positional -- the placeholder must end up in the grammatically correct position in the target language. For numbers and dates, the system goes further: it reformats according to target locale conventions (e.g., 1,234.56 becomes 1.234,56 in German; MM/DD/YYYY becomes DD.MM.YYYY). Brand names from the glossary system are marked as do-not-translate entities, preventing the model from attempting to translate "iPhone" or "Google Maps."