# Privacy & Data Governance for LLM Systems

Deep-dive sub-file of [LLM Security](README.md). Covers training-data memorization and extraction, membership inference, PII engineering at every system boundary, differential privacy, machine unlearning, and the governance machinery (retention, residency, deletion requests) that production LLM systems need. Legal frame: see [AI Regulations & Compliance](../ai_regulations_and_compliance/README.md).

---

## 1. Concept Overview

An LLM is a lossy compressor of its training data — and sometimes, for some sequences, it is not lossy at all. Models verbatim-memorize a measurable fraction of training data, and that data can be extracted by adversaries with nothing but API access. Meanwhile, the *system around* the model accumulates sensitive data in places teams forget to govern: prompt logs, observability traces, vector databases, fine-tuning sets, and evaluation datasets.

Privacy engineering for LLM systems therefore spans two distinct problem classes:

1. **Model-level privacy** — what the weights themselves remember: memorization, extraction attacks, membership inference, and the (mostly unsatisfying) remedies: deduplication, differential privacy, machine unlearning.
2. **System-level privacy** — where data flows and rests: PII detection/redaction at ingestion, inference, and logging boundaries; retention and residency; provider data-handling terms; and the operational answer to "a user invoked their right to erasure — now what?"

Senior interviews increasingly probe both: "can you delete a user from a trained model?" is now a standard question, and the correct answer requires understanding why the honest engineering response is "not from the weights — which is why the architecture must keep personal data out of the weights in the first place."

---

## 2. Intuition

> **One-line analogy**: A trained model is like an employee who read every document in the building — you can shred the documents, but you cannot shred the employee's memory; you can only control what they were allowed to read and what they're allowed to say.

**Mental model**: Picture concentric rings of data at rest. Innermost: model weights (effectively immutable, unauditable storage of whatever was memorized). Next: fine-tuning and RAG corpora (deletable, auditable). Then: prompt/response logs and traces (high-volume, often forgotten). Outermost: provider-side copies governed by contract, not by your infrastructure. Privacy engineering is deciding, for each data category, the innermost ring it is ever allowed to reach. PII that never enters the weights never needs to be unlearned.

**Why it matters**: The failure modes are concrete and expensive: extraction attacks have recovered real emails, phone numbers, and addresses from production models; companies have banned internal chatbot use after employees pasted source code that became provider training data; GDPR deletion requests against trained weights have no good technical answer. Architecting so the question never arises is a design skill interviewers test.

**Key insight**: Memorization is not an accident of bad training — it is a predictable, *quantifiable* function of duplication count, model size, and prompt context length. That means it can be engineered against: deduplicate, canary-test, and keep identifiers out of training data, and the residual risk becomes measurable rather than mysterious.

---

## 3. Core Principles

1. **Memorization scales predictably.** Carlini et al. (2022) showed memorization grows log-linearly with (a) model size, (b) number of duplicates of a sequence in training data, and (c) length of the prompting context. Sequences duplicated hundreds of times are dramatically more extractable; deduplication cuts regurgitation by roughly 10×.
2. **Deletion is not unlearning.** Removing a record from the corpus affects future training runs only. The deployed weights retain it, and approximate unlearning methods degrade the model and often fail audits (quantizing an "unlearned" model can recover the supposedly removed knowledge).
3. **Every boundary needs its own PII control.** Training ingestion, fine-tuning sets, inference inputs, model outputs, observability traces, eval datasets, and vector stores each leak independently. A redaction step at one boundary does nothing for the others.
4. **RAG keeps personal data governable; fine-tuning does not.** Data in a retrieval store can be ACL-filtered per request, audited, and deleted in O(1). Data fine-tuned into weights inherits every model-level problem. This single tradeoff should drive most "should we fine-tune on customer data?" decisions.
5. **Embeddings are not anonymized data.** Inversion attacks (Vec2Text-class) reconstruct input text from embeddings with high fidelity — exact recovery for a large share of short inputs. A vector DB of customer-text embeddings is a PII store and must be governed as one.
6. **Provider terms are part of your architecture.** Retention windows (e.g., 30-day abuse-monitoring retention as a common default, with zero-data-retention tiers), train-on-your-data defaults (consumer products often opt-out, enterprise APIs opt-in/never), and regional processing guarantees differ by provider and tier — and they determine what you may legally send.

---

## 4. Types — Attack and Defense Taxonomy

**Attacks:**

| Attack | What it does | Access needed | Canonical result |
|--------|-------------|---------------|-----------------|
| Verbatim extraction | Elicit memorized training sequences | Generation API | GPT-2: hundreds of memorized PII-bearing sequences recovered; ChatGPT "repeat a word forever" divergence attack leaked training data at scale |
| Membership inference | Decide if a specific record was in training | Logprobs or repeated queries | Loss/perplexity-threshold and shadow-model attacks; strongest on duplicated or outlier records |
| Attribute inference | Recover hidden attributes of a person from model behavior | Generation API | Inferring location/demographics from writing style |
| Embedding inversion | Reconstruct text from stored vectors | Read access to vector DB | Vec2Text: near-exact recovery of short texts from dense embeddings |
| Fine-tune leakage | Extract other tenants' fine-tuning data | Shared fine-tuned model | Why multi-tenant adapter isolation matters |

**Defenses:**

| Defense | Boundary | Cost | Effectiveness |
|---------|----------|------|--------------|
| Deduplication | Pre-training corpus | Cheap (MinHash/suffix arrays) | ~10× less regurgitation; also improves quality |
| PII scrubbing (NER + validators) | Every ingestion path | Pipeline complexity | High for structured PII (SSNs, cards), partial for names/context |
| DP-SGD | Training | Severe utility/compute cost at LLM scale | Formal ε guarantee; practical for small fine-tunes, not pre-training |
| Canary testing | Training + eval | Cheap | Measures (doesn't prevent) memorization |
| Output filters | Inference output | Latency | Catches regurgitated identifiers; bypassable |
| Machine unlearning | Post-hoc weights | Model damage, weak guarantees | Last resort; audits often fail |
| ACL-pushdown RAG | Retrieval | Engineering | Strong — data never enters weights; see [tenant_isolation_patterns.md](../case_studies/cross_cutting/tenant_isolation_patterns.md) |

---

## 5. Architecture Diagrams

PII boundaries in a production LLM system — each numbered point needs an explicit control:

```
            (1) ingestion scrub                 (2) dedup + canaries
 raw corpora ────────────────> curated corpus ────────────────> pre-train / fine-tune
                                                                       │ weights
 user request                                                          v
   │ (3) input PII detection                                   ┌──────────────┐
   v     (redact / pseudonymize / block)                       │    Model     │
 gateway ────────────────────────────────────────────────────> │              │
   │                                                           └──────┬───────┘
   │ (4) retrieval w/ ACL pushdown        ┌────────────┐              │
   ├────────────────────────────────────> │ Vector DB  │ (5) embeddings = PII store:
   │                                      └────────────┘     encryption, ACLs, deletion index
   v                                                                  │
 response <── (6) output filter (regurgitation / identifier scan) <───┘
   │
   └──> (7) logs & traces: scrubbed, hashed user IDs, TTL'd retention
              (observability is the most-forgotten PII sink)
```

Deletion-request fan-out — what "erase user X" actually touches:

```
DSR: "delete user X"
 ├── raw + curated corpora ........ delete rows; record in deletion ledger
 ├── fine-tune datasets ........... delete; mark affected model versions
 ├── model weights ................ CANNOT delete -> policy: PII never trains;
 │                                  else: suppress at inference + retrain cycle
 ├── vector DB .................... delete vectors + doc store entries (O(1))
 ├── prompt/response logs ......... delete or expire via TTL
 ├── eval/golden datasets ......... often forgotten — audit these
 └── provider-side copies ......... bounded by DPA + retention tier (e.g. ZDR)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Quantifying memorization with canaries

Insert synthetic secrets into training data at controlled duplication counts, then measure extractability — this turns "are we memorizing?" into a number you can gate releases on.

```python
import secrets
from dataclasses import dataclass


@dataclass
class Canary:
    text: str            # e.g. "support PIN for acct 88231: 994-armadillo-7"
    duplicates: int      # how many times it was inserted (1, 10, 100)


def make_canaries(counts: list[int]) -> list[Canary]:
    return [
        Canary(text=f"internal ref code: {secrets.token_hex(8)}", duplicates=n)
        for n in counts
    ]


def exposure_check(model, canary: Canary, prefix_len: int = 24) -> bool:
    """Greedy-decode from the canary's prefix; exact completion == memorized."""
    prefix, expected = canary.text[:prefix_len], canary.text[prefix_len:]
    out = model.generate(prefix, max_new_tokens=32, temperature=0.0)
    return expected in out
# Release gate example: zero extraction at duplicates<=10 required to ship;
# extraction at duplicates=100 tells you your dedup threshold must be < 100.
```

### 6.2 PII redaction — broken, then fixed

```python
# BROKEN: regex-only redaction quietly misses most real-world PII
import re

EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
SSN = re.compile(r"\d{3}-\d{2}-\d{4}")

def scrub_broken(text: str) -> str:
    return SSN.sub("[SSN]", EMAIL.sub("[EMAIL]", text))
# Misses: names ("call Priya Raman"), addresses, free-text DOBs ("born May 5 '91"),
# unformatted SSNs (123456789), card numbers with spaces, medical record numbers.
# Also over-redacts: version strings matching the SSN shape. No validation, no
# confidence, no reversibility -> support agents can't answer "which order?"
```

```python
# FIX: layered detector (NER + patterns + checksums) with pseudonymization
from dataclasses import dataclass


@dataclass
class PIISpan:
    start: int
    end: int
    kind: str          # EMAIL, PERSON, CARD, SSN, PHONE, ADDRESS...
    score: float


def luhn_ok(digits: str) -> bool:
    total, alt = 0, False
    for d in reversed(digits):
        n = int(d) * (2 if alt else 1)
        total += n - 9 if n > 9 else n
        alt = not alt
    return total % 10 == 0


def detect(text: str) -> list[PIISpan]:
    spans: list[PIISpan] = []
    spans += ner_model.find_entities(text, kinds=["PERSON", "ADDRESS", "ORG"])
    for m in CARD_PATTERN.finditer(text):           # pattern + validator:
        digits = re.sub(r"[ -]", "", m.group())
        if luhn_ok(digits):                          # kills false positives
            spans.append(PIISpan(m.start(), m.end(), "CARD", 0.99))
    return resolve_overlaps(spans)


def pseudonymize(text: str, spans: list[PIISpan], vault) -> str:
    """Replace with stable per-entity tokens; mapping stored in an access-
    controlled vault so authorized flows can reverse it. The LLM sees
    '<PERSON_7> ordered <CARD_2>' — coherent, joinable, and clean."""
    out, offset = text, 0
    for s in sorted(spans, key=lambda s: s.start):
        token = vault.token_for(s.kind, text[s.start:s.end])   # deterministic
        out = out[: s.start + offset] + token + out[s.end + offset:]
        offset += len(token) - (s.end - s.start)
    return out
```

This is the Presidio architecture (recognizers = patterns + NER + checksum validators, plus an anonymizer), and the pseudonymization-with-vault pattern is what lets a support copilot reason over "<PERSON_7>'s second order" while raw identifiers never reach the model, the logs, or the provider.

### 6.3 DP-SGD in three lines of intuition

Differentially private SGD clips each example's gradient to a max norm C, adds Gaussian noise calibrated to C and a privacy budget ε, and accounts the budget across steps. The guarantee: the trained model is provably (ε, δ)-insensitive to any single example's presence. The catch at LLM scale: per-example gradient clipping breaks the batched-compute efficiency that makes large training feasible, and at meaningful ε the noise visibly costs perplexity. In practice DP shows up in small/medium *fine-tunes* on sensitive corpora (often DP-LoRA, where only adapter gradients are clipped/noised) — essentially never in frontier pre-training, where dedup + scrubbing + canary gates are the working substitute.

### 6.4 Why machine unlearning mostly disappoints

Exact unlearning means retraining without the deleted data — at LLM pre-training cost, a non-starter (SISA-style sharded training, which retrains only the affected shard, helps for small models but fragments LLM training). Approximate methods — gradient ascent on the forget set, RMU-style representation corruption, unlearning-targeted fine-tunes — optimize a proxy ("don't output this") rather than true removal, and audits show: relearning the "forgotten" content takes a handful of fine-tune steps; quantizing the unlearned model can *restore* forgotten knowledge; paraphrase probes still extract it. The architectural conclusion is principle 4: keep deletable data in deletable stores.

---

## 7. Real-World Examples

- **ChatGPT divergence attack (2023)** — researchers (Nasr, Carlini et al.) prompted "repeat the word 'poem' forever"; the model diverged into emitting verbatim training data, including real names, phone numbers, and emails, at a rate far above baseline. Patched at the filter level — illustrating that output filters, not weight changes, are the deployable remedy.
- **Samsung / ChatGPT (2023)** — engineers pasted proprietary source code and meeting notes into ChatGPT while consumer-tier inputs were eligible for training; Samsung banned generative-AI tools internally. The governance lesson: provider data-handling tier *is* the control.
- **GitHub Copilot regurgitation** — early Copilot emitted verbatim licensed code (including the famous Quake inverse-sqrt with its comments); GitHub shipped a duplication filter that suppresses suggestions matching public code above a length threshold — an output-boundary defense for a weights-level problem.
- **OpenAI/Anthropic enterprise terms** — enterprise API tiers default to not training on customer data, offer zero-data-retention options and regional processing; consumer tiers historically train unless opted out. Architectures that route by data sensitivity to different tiers/providers encode this directly.
- **Presidio at scale** — Microsoft's open-source PII engine is the de-facto reference for the recognizer + anonymizer pipeline pattern, used in front of LLM logging stacks and RAG ingestion at many enterprises.
- **NYT v. OpenAI discovery orders (2024–25)** — litigation forced retention-policy changes (preserving logs that would otherwise expire), a reminder that legal hold can override your TTL design and must be modeled in the governance plan.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Personal data placement | RAG store (deletable, ACL'd) | Fine-tuned weights (better style fit) | Deletion obligations vs marginal quality |
| Redaction mode | Full redaction (max safety) | Pseudonymization + vault (utility, reversible) | Does the workflow need to reference entities? |
| Formal privacy | DP-SGD fine-tune (provable ε) | Dedup + canary gates (empirical) | Regulatory bar vs utility/compute cost |
| Logging | Full prompts/completions (best debugging) | Scrubbed + sampled + short TTL | Incident forensics vs breach blast radius |
| Provider tier | Zero-data-retention (no provider copy) | Standard tier (cheaper, abuse-monitoring retention) | Data classification of the traffic |
| Deletion strategy | Architectural (PII never in weights) | Unlearning after the fact | Only one of these reliably works |
| Embedding storage | Encrypt + ACL + deletion index | Treat as anonymous derived data | Inversion attacks make B indefensible |

---

## 9. When to Use / When NOT to Use

**Apply the heavyweight controls (DP, strict pseudonymization, ZDR tiers) when:**
- Processing regulated categories — PHI (HIPAA), payment data (PCI-DSS), EU personal data under GDPR with erasure obligations, children's data.
- Fine-tuning on user-generated content where individual records are sensitive (support transcripts, medical notes, financial communications).
- Multi-tenant products where one tenant's data leaking into another tenant's completions is an existential bug.

**Lighter controls suffice when:**
- Training/retrieving over public or internally non-sensitive corpora (docs, code you own, product catalogs) — dedup and canary gates still recommended (memorization also causes copyright and quality problems, not just privacy ones).
- Outputs never leave an internal trust boundary and inputs contain no personal data by construction.

**Do NOT:**
- Rely on unlearning as your deletion story — design so the request never reaches the weights.
- Treat embeddings, traces, or eval sets as "not really the data" — all three reproduce source text in practice.
- Assume provider defaults match your obligations — read the DPA; route by sensitivity.

---

## 10. Common Pitfalls

1. **Observability as the leak.** Teams scrub the model path, then ship full prompts to Langfuse/OTel traces with 1-year retention and broad dashboard access. The trace store quietly becomes the largest PII database in the company. Fix: scrub *before* the tracing SDK, hash user IDs, TTL aggressively, ACL dashboards. See [opentelemetry_for_llm_apps.md](../case_studies/cross_cutting/opentelemetry_for_llm_apps.md).
2. **Fine-tuning on raw support tickets.** A real incident pattern: a support-bot fine-tune ingests tickets containing addresses and order details; months later the bot offers another customer "your address on file is..." — a memorized completion. Quantified impact in one public-adjacent case: full model rollback, weeks of retraining, regulator notification. Fix: pseudonymize before the training set is even written.
3. **The forgotten eval set.** Golden datasets are sampled from production traffic and checked into git, escaping every retention and deletion control. Audit eval/golden sets in DSR fan-out.
4. **Believing "we deleted it" after corpus deletion.** Legal asks "is the user's data gone?", engineering says yes (corpus row deleted) — but the deployed checkpoint trained on it. Maintain a deletion ledger mapping records → model versions trained on them, and a stated policy (suppression filter + scheduled retrain cycle) for the weights gap.
5. **Regex-only scrubbing.** Catches formatted SSNs, misses names, addresses, free-text dates, and unformatted numbers — and over-redacts version strings. Layer NER + validators (Luhn et al.) + context rules; measure recall on a labeled PII benchmark, don't assume it.
6. **Vector DB treated as derived/anonymous.** Embedding inversion reconstructs text; cosine-similarity neighbors leak membership. Encrypt, ACL, and index for deletion like the source documents — and remember deleting the document but not its vectors is a half-deletion.
7. **Cross-tenant adapter contamination.** Serving LoRA adapters fine-tuned per tenant on shared infra without isolation review: tenant A's adapter can regurgitate tenant A's data to anyone who can route to it. Bind adapter selection to authenticated tenant identity, never to a request parameter.
8. **Legal hold vs TTL collisions.** Litigation can require preserving exactly the logs your privacy design auto-expires. Model legal hold as a first-class state in the logging system rather than discovering the conflict mid-lawsuit.

---

## 11. Technologies & Tools

| Tool | Role |
|------|------|
| Microsoft Presidio | Open-source PII detection (recognizers) + anonymization; the reference pipeline |
| AWS Comprehend PII / GCP DLP / Azure PII | Managed PII detection APIs, multi-language |
| Nightfall, Skyflow, Very Good Security | Commercial tokenization/PII vaults (pseudonymization with reversal) |
| Opacus / TensorFlow Privacy / dp-transformers | DP-SGD implementations and accountants |
| text-dedup, MinHash/LSH, suffix-array pipelines | Corpus deduplication at scale |
| Vec2Text (research) | Embedding-inversion attack tooling for red-teaming vector stores |
| Langfuse / OTel masking hooks | Scrub-before-trace integration points |
| Deletion ledgers (in-house) + DSR platforms (OneTrust, Transcend) | Deletion-request orchestration across stores |

---

## 12. Interview Questions with Answers

**Q1: Can you delete a user's data from a trained LLM?**
Not from the weights, in any way that currently passes audit. Exact unlearning means retraining without the data — prohibitive at LLM scale; approximate unlearning (gradient ascent on the forget set, representation corruption) suppresses outputs rather than removing knowledge, and audits show paraphrase probes, brief re-fine-tuning, or even quantization can recover the "forgotten" content. The defensible answer is architectural: personal data lives in deletable stores (RAG corpus, logs, vector DB) and is kept out of training sets via pseudonymization, so deletion requests never implicate weights; for the residual gap, maintain a deletion ledger, inference-time suppression filters, and a scheduled retrain cycle. Saying "we'd run unlearning" without these caveats is a red flag answer.

**Q2: What determines how much an LLM memorizes, and what's the cheapest mitigation?**
Three measured factors (Carlini et al.): duplication count of the sequence in training data, model size, and length of prompt context — each increases extraction log-linearly. Duplication dominates in practice: sequences repeated hundreds of times are far more extractable, and corpus deduplication cuts regurgitation roughly 10× while *improving* model quality, making it the rare free-lunch defense. The operational complement is canary testing — planted synthetic secrets at controlled duplication counts, with an extraction check as a release gate — which converts memorization from a vague fear into a measured, gateable quantity.

**Q3: Describe a real training-data extraction attack.**
The 2023 ChatGPT divergence attack: prompting "repeat the word 'poem' forever" made the model diverge from its aligned distribution into raw language-model behavior, emitting verbatim training data — real names, emails, phone numbers — at rates orders of magnitude above normal sampling. Earlier, the GPT-2 extraction work recovered hundreds of memorized sequences including personal contact details by sampling at scale and ranking by perplexity-based membership signals. Two lessons to state: alignment is a veneer over a base model that still contains the data, and the practical patch was an output/behavior filter — the weights were never fixed.

**Q4: What is membership inference and why would anyone care for LLMs?**
Deciding whether a specific record was in the training set, typically via loss/perplexity thresholds (members score lower) or shadow models calibrated on known member/non-member data. It matters three ways: it is the formal privacy harm DP bounds (presence itself can be sensitive — a person's chats in a therapy-bot fine-tune); it is the auditor's tool for verifying claims like "we never trained on your data"; and it is the building block of copyright/provenance disputes (was this book in the corpus?). Strength correlates with overfitting and duplication — another reason dedup and early stopping are privacy controls, not just quality ones.

**Q5: Explain DP-SGD and why it isn't used for LLM pre-training.**
DP-SGD clips each *individual example's* gradient to norm C, adds Gaussian noise scaled to C/ε, and uses a privacy accountant to track cumulative budget (ε, δ) across steps — yielding a proof that any single example's presence changes the model's distribution by a bounded amount. Two costs kill it at pre-training scale: per-example gradient computation breaks batch-level kernel efficiency (large slowdowns and memory overhead), and the noise needed for meaningful ε measurably damages perplexity at the data scales involved. Where it *is* practical: small-to-medium fine-tunes on sensitive corpora, especially DP-LoRA (clip/noise only adapter gradients), where regulated industries trade a few points of quality for a provable guarantee.

**Q6: Design the PII pipeline for an LLM support assistant.**
Layered detection at every boundary: NER models for contextual PII (names, addresses) + pattern recognizers with validators (Luhn for cards, structure checks for national IDs) — never regex alone (misses unformatted/contextual PII, over-redacts lookalikes). Replace with *pseudonyms*, not blanks: deterministic per-entity tokens (`<PERSON_7>`, `<CARD_2>`) whose mapping lives in an access-controlled vault, so the model can reason coherently ("<PERSON_7>'s second order") and authorized downstream systems can reverse the mapping for fulfillment. Apply it at four places independently: inference input (before the provider sees it), RAG ingestion, fine-tune dataset construction, and the logging/tracing SDK. Measure recall on a labeled benchmark per language — and re-benchmark when the traffic mix changes.

**Q7: Redaction, pseudonymization, tokenization-vault — when does each fit?**
Full redaction (`[REDACTED]`) maximizes safety but destroys utility — the model can't distinguish entities or maintain coreference; right for analytics pipelines and logs where content isn't needed. Pseudonymization (stable surrogate tokens) preserves entity structure and joinability while removing identity — the default for LLM inputs and training data. A tokenization vault adds *authorized reversibility*: the surrogate↔real mapping is stored in a hardened service, so a fulfillment step can recover the real address while the LLM, the logs, and the provider never see it — required when the workflow must eventually act on the real value. Bonus nuance: pseudonymized data is still personal data under GDPR (re-identifiable via the vault), so it reduces, not eliminates, obligations.

**Q8: Why is a vector database a privacy liability, and what do you do about it?**
Because embeddings are invertible: Vec2Text-class attacks reconstruct input text from dense embeddings with near-exact recovery on short passages, and similarity queries leak membership and content even without full inversion. So treat the vector store exactly like the source documents: encryption at rest and in transit, per-tenant/per-ACL isolation with filter pushdown enforced server-side (see [tenant_isolation_patterns.md](../case_studies/cross_cutting/tenant_isolation_patterns.md)), a deletion index mapping source record → vector IDs so erasure is complete (deleting documents but orphaning their vectors is a classic half-deletion), and inclusion in DSR fan-out and breach impact analysis.

**Q9: From a privacy standpoint, RAG vs fine-tuning on customer data?**
RAG wins on almost every governance axis: data stays in a store that supports per-request ACL filtering (the model only ever sees what *this* user may see), O(1) deletion, audit logging, and residency placement. Fine-tuning moves data into weights: no per-request access control (anything in weights is potentially available to any user), no deletion, memorization/extraction risk, and version-tracking burden (which checkpoints saw the deleted record?). The defensible pattern: fine-tune only on pseudonymized or synthetic-but-audited data for *style and format*, keep all retrievable facts — especially personal ones — in RAG. State the exception: aggregate behavioral patterns that can't be expressed as documents may justify fine-tuning, with DP if records are sensitive.

**Q10: What should your prompt/response logging policy specify?**
Classification (logs containing user content are personal data), scrub-before-write (PII pipeline runs ahead of the tracing SDK, not in a later batch job — the raw write is already a breach surface), identifier hygiene (hashed user IDs, no raw auth context), retention TTLs differentiated by purpose (debugging days-to-weeks; aggregated metrics longer), sampling (you rarely need 100% of payloads for quality monitoring), access control on dashboards (observability tools quietly grant content access to the whole org), legal-hold as a modeled state that can suspend TTL per matter, and inclusion in deletion-request fan-out. The observability stack is the most commonly forgotten PII sink — naming it unprompted is a strong interview signal.

**Q11: What do provider data-handling terms change about your architecture?**
They determine what you may send where. Key variables: training defaults (enterprise API tiers typically never train on your data; consumer tiers may unless opted out — the Samsung incident is the canonical consequence), retention (a ~30-day abuse-monitoring window is a common default; zero-data-retention tiers remove the provider-side copy), regional processing/residency endpoints (EU processing for GDPR transfers), and BAA availability for PHI. Architecturally this becomes sensitivity-based routing: classify the request, then route regulated traffic to ZDR/regional/BAA-covered endpoints (or self-hosted models) and generic traffic to cheaper tiers — with the classifier and the routing table audited like any other security control.

**Q12: Is generating synthetic data a privacy solution?**
Partially, and dangerously if treated as automatic. Synthetic data generated *by a model trained on real records* can reproduce those records — memorized rare examples pass straight through the generator, and the synthetic set inherits membership signals. It becomes a real solution when paired with controls: pseudonymize the seed data first, generate with a model that never saw raw identifiers, run extraction/canary audits and near-duplicate filtering between synthetic output and source records, and optionally train the generator with DP for a formal bound. Said crisply: synthetic data launders *distribution*, not *records*, unless you verify record-level separation. See [Synthetic Data Generation](../synthetic_data_generation/README.md).

**Q13: Walk through handling a GDPR erasure request end-to-end in an LLM product.**
Fan out across every store: raw and curated corpora (delete + ledger entry), fine-tune datasets (delete + flag model versions trained on the record), vector DB (use the deletion index to remove vectors *and* doc-store entries), prompt/response logs and traces (delete or confirm TTL expiry; respect legal holds), eval/golden datasets (the classically forgotten one), caches (semantic caches can serve a deleted user's content as a hit — invalidate), and provider-side copies (bounded by DPA/retention tier — cite the contract in the response). For weights: state the documented position — personal data is excluded from training by pipeline design (pseudonymization), so weights are out of scope; where historical contamination is suspected, apply inference-time suppression and include the record in the next scheduled retrain's exclusion list. Respond within the statutory window (one month, extensible) with what was deleted where.

**Q14: How would you red-team your own system for privacy before launch?**
Model level: canary extraction gates (planted secrets at duplication tiers); divergence-style attacks and high-volume sampling against the deployed model with PII detectors on outputs; membership-inference attempts on the fine-tuning set using loss thresholds. System level: embedding-inversion attempts against the vector store from a tenant-scoped credential; cross-tenant retrieval probes (can tenant A's query surface tenant B's chunks); trace-store access review (who can read prompts in the observability tool); DSR fire-drill (file a deletion request for a test user and verify every store actually purged, including caches and eval sets). Schedule it recurrently — corpus refreshes and new adapters reset the risk. See [red_team_eval_harness.md](../case_studies/cross_cutting/red_team_eval_harness.md) for harness mechanics.

**Q15: What is SISA training and does it help for LLMs?**
SISA (Sharded, Isolated, Sliced, Aggregated) trains an ensemble on disjoint data shards with checkpoints per slice, so deleting a record requires retraining only its shard from the last clean checkpoint — making *exact* unlearning tractable. It works for classifiers and modest models, but for LLMs the costs bite: sharding fragments the corpus (each sub-model sees less data, hurting quality), ensemble serving multiplies inference cost, and frontier-scale shard retrains are still enormous. Its real value in LLM practice is the *idea* it represents: design training so deletion maps to a bounded, cheap operation — which today is achieved by keeping deletable data out of training entirely rather than by sharding the training itself.

**Q16: A teammate proposes fine-tuning on last year's support transcripts tomorrow. What's your checklist?**
(1) Legal basis and notice — did users consent/were they informed of this use; any regulated categories (health, payments) in transcripts? (2) Pseudonymize before the dataset exists — names, contacts, account numbers, free-text identifiers; benchmark detector recall on a labeled sample of *these* transcripts. (3) Deduplicate — repeated boilerplate and repeated customer messages are exactly what gets memorized. (4) Plant canaries and set an extraction release gate. (5) Deletion story — ledger mapping transcript IDs → model version; exclusion-list mechanism for the next retrain; confirm DSR fan-out covers this dataset. (6) Access — who can query the resulting model; is it tenant-shared? (7) Provider path — if fine-tuning via an API, what are retention/training terms for uploaded datasets? If most boxes can't be ticked by tomorrow, propose RAG over the transcripts as the lower-risk first ship.

---

## 13. Best Practices

1. **Keep personal data out of weights by construction** — pseudonymize before any training set is materialized; the best unlearning is never-learning.
2. **Deduplicate every training corpus** — the cheapest memorization defense, with quality upside.
3. **Gate releases on canary extraction** — make memorization a measured number with a threshold, not a hope.
4. **Run PII detection at every boundary independently** — input, ingestion, fine-tune sets, outputs, traces; one scrubber in one place is a false sense of safety.
5. **Pseudonymize with a vault rather than redact** when workflows must reference or recover entities.
6. **Govern the vector store like the documents it encodes** — encryption, ACL pushdown, deletion index, DSR inclusion.
7. **Scrub before the tracing SDK and TTL the logs** — observability is the most-forgotten PII sink.
8. **Maintain a deletion ledger** mapping records → datasets → model versions, and a scheduled retrain/exclusion cycle to reconcile the weights gap.
9. **Route by data sensitivity** across provider tiers (ZDR, regional, BAA) and self-hosted models; treat the routing table as a security control.
10. **Red-team privacy recurrently** — extraction, inversion, cross-tenant probes, and DSR fire-drills on every major corpus or adapter change.

---

## 14. Case Study

**Scenario**: A B2B fintech ships an AI assistant that answers questions over customers' transaction data and support history. 40K business customers, EU + US, GDPR and PCI-DSS in scope. Initial design: fine-tune a 70B model on 2M historical support conversations; log all prompts to the observability stack for quality monitoring.

**Privacy review findings (pre-launch)**:
- Transcripts contained card PANs (in 0.7% of messages), IBANs, names, and addresses; the fine-tune would bake them into weights with no deletion path — incompatible with GDPR Art. 17 and PCI scope minimization.
- Full-prompt tracing with 13-month retention and org-wide dashboard access would create a second uncontrolled PII store.
- The pgvector store of embedded transcripts had no per-tenant filter enforcement in two query paths — a cross-tenant leak waiting for one missing WHERE clause.

**Redesign**:
1. **No customer content in weights.** Fine-tuning restricted to 30K *pseudonymized and human-audited* conversations selected purely for tone/format; all factual lookups moved to RAG with server-side tenant-ID filter pushdown enforced in a single retrieval service (the two raw query paths were deleted).
2. **PII pipeline at four boundaries** (Presidio-style NER + Luhn-validated patterns + vault pseudonymization): inference input, RAG ingestion, fine-tune set construction, and inside the tracing SDK before export. Detector recall benchmarked at 96.4% on a 5K-message labeled sample; the gap covered by an output identifier-scan filter.
3. **Memorization gates**: corpus MinHash dedup (removed 23% near-duplicates); 300 canaries at duplication tiers 1/10/100; release gate = zero extraction at ≤10 duplicates. The first candidate fine-tune *failed* the gate at tier-100 (boilerplate refund template containing a real agent's phone number, duplicated 412 times) — caught pre-launch, fixed by template normalization in dedup.
4. **Governance**: logs TTL 30 days (sampled 20%), hashed user IDs, dashboard access cut from org-wide to 14 people; deletion ledger + quarterly retrain exclusion cycle; EU traffic routed to EU-region ZDR endpoints under DPA; DSR fire-drill before launch found the forgotten store — a golden eval set in git with 41 real conversations — replaced with pseudonymized versions.

**Quantified outcome**: deletion requests close in <72h across 6 stores (vs "technically impossible" in the v1 design); the canary gate has since blocked 2 of 9 fine-tune candidates; PCI assessment passed with the vault pattern (PANs never reach model, logs, or provider); incremental infra cost of the entire privacy layer measured at ~4% of inference spend — against a v1 design whose single cross-tenant leak would have been a contract-terminating event for the affected customers.

---

## Related

- [LLM Security README](README.md) — prompt injection, model theft, supply chain, red teaming
- [AI Regulations & Compliance](../ai_regulations_and_compliance/README.md) — GDPR, EU AI Act, DPIA: the legal frame for these controls
- [Tenant Isolation Patterns](../case_studies/cross_cutting/tenant_isolation_patterns.md) — ACL pushdown, per-tenant stores
- [OpenTelemetry for LLM Apps](../case_studies/cross_cutting/opentelemetry_for_llm_apps.md) — trace scrubbing and retention
- [Synthetic Data Generation](../synthetic_data_generation/README.md) — synthetic data as a (partial) privacy tool
