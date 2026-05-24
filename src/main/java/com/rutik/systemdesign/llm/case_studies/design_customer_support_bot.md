# Case Study: Design an AI Customer Support Bot

## Intuition

> **Design intuition**: A customer support bot is a RAG + tools system with a human escalation path — the key design challenges are intent classification (support vs. off-topic), knowledge retrieval accuracy (wrong answer is worse than "I don't know"), safe tool use (update records only when confident), and graceful handoff to human agents.

**Key insight for this design**: The decision of when to escalate to a human is the most critical design decision. False positives (unnecessary escalations) waste human agent time; false negatives (bot handles something it shouldn't) damage customer trust. A confidence-based routing system with clear escalation triggers is more important than maximizing bot resolution rate.

---

## 1. Requirements Clarification

### Functional Requirements
- Handle customer inquiries across channels: chat widget, email, mobile app
- Resolve Tier-1 issues autonomously (FAQs, order status, account info)
- Escalate complex/sensitive issues to human agents with full context handoff
- Multi-turn conversations: maintain context within a session
- Tool use: lookup orders, accounts, policies; process refunds; update records
- Multilingual: support 15 languages
- Agent assist mode: suggest responses to human agents in real time
- Analytics dashboard: resolution rate, CSAT, escalation patterns

### Non-Functional Requirements
- **Response latency**: < 2 seconds for first response
- **Resolution rate**: > 60% of inquiries handled without human escalation
- **CSAT**: > 4.2/5.0 average customer satisfaction
- **Availability**: 99.9% (customer support is 24/7)
- **Scale**: 1M conversations/day; 50K concurrent sessions peak

### Out of Scope
- Ticketing system (use Zendesk/Salesforce)
- Human agent routing platform
- Customer authentication (use existing auth system)

---

## 2. Scale Estimation

### Traffic Estimates
```
Daily conversations: 1M
Average messages per conversation: 8 (4 user + 4 bot)
Daily messages: 8M
Peak concurrent sessions: 50,000 (assume 30-minute sessions)
Peak session creation rate: 50,000 / 1800 = ~28 new sessions/second

Token estimates per message:
  Context (conversation history + retrieved KB): 2,000 tokens
  Bot response: 200 tokens
  Total per turn: 2,200 tokens

Daily token cost:
  4M bot turns × 2,200 tokens = 8.8B tokens/day
  Input tokens: ~8B; Output tokens: ~0.8B
```

### Storage Estimates
```
Conversation storage:
  1M conversations × 20KB average = 20GB/day
  Retention: 12 months for compliance → 7.3TB total

Knowledge base:
  50,000 FAQ articles + policy documents
  Average: 500 tokens per article = 25M tokens
  Embeddings: 50K articles × 1536 dims × 4 bytes = 307MB (tiny!)

Customer data cache (session):
  50K concurrent sessions × 50KB = 2.5GB in Redis
```

---

## 3. High-Level Architecture

```
Customer
  |
  | (Web Chat / Mobile / Email)
  v
[Channel Adapter Layer]
  - Chat widget: WebSocket / SSE
  - Email: Gmail/Outlook webhook → normalize to conversation format
  - SMS/WhatsApp: Twilio webhook → normalize
  |
  v
[Session Manager]
  - Create/retrieve session (Redis)
  - Authentication: link to customer account
  - Conversation history tracking
  |
  v
[Intent & Routing Engine]
  ┌──────────────────────────────────────────┐
  │  Fast classifier (BERT-small, 10ms):     │
  │  - Intent: billing | order | technical   │
  │            account | complaint | other   │
  │  - Urgency: low | medium | high          │
  │  - Sentiment: positive | neutral | angry │
  │  - Language detection                    │
  └──────────────────────────────────────────┘
          |
    ┌─────┴──────────────────────────────────────────┐
    │                    │                            │
    ▼                    ▼                            ▼
[Immediate          [Bot Handler]             [Human Queue]
 Escalation]         Full RAG + LLM           (angry/fraud/
  (threats,          pipeline                  legal/explicit
   self-harm,                                  escalation request)
   legal threats)
    |
    v
[Context Builder]
  - Fetch customer data (orders, account history, prior tickets)
  - Retrieve KB articles (RAG over knowledge base)
  - Format conversation history
    |
    v
[LLM Response Generator]
  - GPT-4o / Claude 3.5 with system prompt
  - Tool use: CRM lookups, policy checks, refund processing
  - Multi-language response generation
    |
    v
[Response Validation]
  - Factual check: does answer match KB?
  - Confidence score: is bot sure enough to respond autonomously?
  - Safety filter: no inappropriate content
  - Escalation trigger: if low confidence → offer human agent
    |
    v
[Response Delivery]
  - Send to customer
  - Update conversation log
  - Trigger CSAT survey at session end
    |
    v
[Analytics & Learning]
  - Log outcome: resolved/escalated/abandoned
  - Update CSAT scores
  - Feed back low-confidence cases for KB improvement
```

---

## 4. Component Deep Dives

### 4.1 Intent Classification and Routing

```
Two-stage classification:

Stage 1: Fast safety classifier (runs first, < 5ms)
  Categories: safe | immediate_escalation | content_warning
  Triggers for immediate escalation:
    - Self-harm or crisis keywords
    - Threats to company or staff
    - Explicit legal action mentions ("I'm suing", "my attorney")
    - Fraud indicators ("this is fraudulent", "stolen card")
    → Route directly to human, bypass bot entirely

Stage 2: Intent + urgency classifier (BERT-small, 10ms)
  Intents (multi-label):
    billing: invoices, charges, payments, refunds
    order: status, tracking, cancel, modify
    technical: product issues, bugs, how-to, setup
    account: password, profile, subscription, privacy
    complaint: escalation language, frustrated, dissatisfied
    general: general questions, browsing

  Urgency:
    High: "urgent", "immediately", "ASAP", time pressure
    Medium: default
    Low: informational, no expressed urgency

  Sentiment:
    Positive: happy, thanks, working well
    Neutral: factual questions
    Negative: frustrated, angry, disappointed
    Very negative (anger score > 0.8) → flag for early escalation

Routing decision matrix:
  Immediate escalation flag → human (skip bot entirely)
  Anger score > 0.8 AND issue > 2 turns unresolved → escalate
  Intent = "billing" AND amount > $500 → prefer human
  Intent = "technical" AND product = "enterprise" → route to specialized team
  Default → bot handler
```

### 4.2 Knowledge Base RAG

```
Knowledge base structure:
  50,000 articles organized by:
    Category (billing, orders, technical, account, policies)
    Product line
    Language (15 languages; each article translated)

  Article format:
    {
      id: "kb-123",
      title: "How to Request a Refund",
      category: "billing",
      content: "To request a refund...",
      language: "en",
      product: "all",
      last_updated: "2024-03-01",
      resolution_rate: 0.78  // % of time citing this article resolved issue
    }

Retrieval pipeline:
  1. Query = customer message + conversation summary (last 3 turns)
  2. Language-aware embedding: use multilingual-e5-large model
     - Embed query in detected language
     - Cross-lingual: query in Spanish → match English articles → translate
  3. Qdrant search with category filter:
     filter = {category: detected_intent, language: EN_or_native}
     top_k = 20
  4. Reranker: cross-encoder on top-20 → top-5

Multilingual handling:
  Option A (translate-then-search): translate query to English → search English KB
    Pros: one index, simple; Cons: translation latency, subtle meaning loss
  Option B (multilingual embeddings): embed in native language
    Pros: no translation needed; Cons: multilingual model slightly worse quality
  Option C (dual search): search both language-native and English, merge
    Chosen: Option B (multilingual-e5-large) with Option A fallback

Customer data enrichment (NOT from KB, but from CRM tools):
  - Order status: lookup from Order DB
  - Account tier: affects response (VIP gets different treatment)
  - Prior ticket history: avoid asking customer to repeat information
  - Current subscription: relevant for billing questions
```

### 4.3 Tool Use for Action-Taking

```
Bot isn't just answering questions — it takes actions:

Available tools:
  get_order(order_id) → {status, items, delivery_date, tracking}
  get_account(customer_id) → {tier, subscription, balance, preferences}
  get_ticket_history(customer_id) → [prior_tickets]
  check_refund_eligibility(order_id) → {eligible, reason, max_amount}
  process_refund(order_id, amount, reason) → {confirmation_id}
  update_account(customer_id, field, value) → {success}
  schedule_callback(customer_id, time) → {confirmation}

Tool use decision:
  If question is answerable from KB alone → no tool call (faster)
  If needs real-time data (order status, account balance) → tool call first

Example conversation:
  Customer: "Where is my order #12345?"
    → Intent: order_status
    → Tool call: get_order("12345")
    → Result: {"status": "shipped", "tracking": "UPS-789", "eta": "tomorrow"}
    → Response: "Your order #12345 has shipped! Expected delivery is tomorrow.
                 Track it here: ups.com/track/UPS-789"

  Customer: "Can I get a refund?"
    → Intent: refund_request
    → Tool call: check_refund_eligibility("12345")
    → Result: {"eligible": true, "reason": "within 30-day window", "max_amount": 49.99}
    → Tool call: process_refund("12345", 49.99, "customer_request")
    → Result: {"confirmation_id": "REF-456", "processed": "3-5 business days"}
    → Response: "I've processed your refund of $49.99 (Confirmation: REF-456).
                 It will appear in 3-5 business days."

Guardrails on actions:
  Refund limits: bot can process up to $100; above → human approval required
  Account changes: email/password changes → always require human verification
  Cancellations: subscription cancellations → offer retention first, then human
  Returns: above 30-day policy → bot declines and offers escalation
```

### 4.4 Escalation Design

```
Escalation is the most important bot failure mode to get right.

Types of escalation:

1. Proactive escalation (bot decides to escalate):
   Triggers:
   - Confidence score < 0.6 (bot not sure it's right)
   - Issue unresolved after 3 turns on same topic
   - Customer explicitly asks for human
   - Anger detected (sentiment score < -0.8)
   - Issue type outside bot's scope (fraud, legal)
   - VIP customer dissatisfied

2. Reactive escalation (customer requests):
   Phrases: "talk to a person", "real agent", "human", "supervisor"
   Action: immediately escalate; no attempt to retain in bot

3. Graceful escalation message:
   "I want to make sure you get the best help possible. Let me connect you
   with a specialist who can better assist with [summarized issue].
   Average wait time: 3 minutes. Would you like to continue with a human
   agent, or can I help with anything else?"

Context handoff to human agent:
  {
    customer_id: "cust_123",
    account_tier: "premium",
    conversation_summary: "Customer asking about order #12345 not received.
                           Bot confirmed shipped but customer disputes delivery.",
    key_facts: ["Order #12345", "Expected: March 10", "Status: Delivered per UPS"],
    sentiment: "frustrated",
    prior_tickets: [{"id": "TKT-789", "resolved": true, "topic": "billing"}],
    full_conversation: [...],
    escalation_reason: "customer disputed delivery; requires manual investigation"
  }

Benefit: human agent reads summary, not 20 turns of conversation.
         Human starts with context; customer doesn't have to repeat everything.
```

### 4.5 Agent Assist Mode

```
For conversations that reach human agents:
Bot continues running in "assist mode" — suggesting responses in real time.

Human agent interface:
  Left panel: Customer conversation (live)
  Right panel: Bot suggestions (3 suggested responses, ranked)
              + KB article links
              + Customer account summary
              + Similar resolved tickets

Suggestion generation:
  Trigger: human agent receives new customer message
  Bot analyzes: message + conversation context + customer data
  Generates: 3 suggested responses (terse, detailed, empathetic variants)
  Latency: < 1s (agent needs it before they start typing)

KB article recommendations:
  Real-time search: as conversation progresses, suggest relevant articles
  Agent can: insert article content into reply, attach article link to response

Similar ticket lookup:
  Embed current conversation summary → search resolved tickets
  Show: top-3 most similar resolved tickets with their solutions

Value of agent assist:
  - New agents: 40% faster resolution (guided by suggestions)
  - Consistency: all agents give same-quality answers
  - Training: agents learn from bot suggestions over time
  - Cost: junior agents can handle more complex issues with AI support
```

---

## 5. Analytics and Continuous Improvement

```
Core metrics tracked:

Resolution rate:
  Automated resolution / total conversations
  By intent type (shows where bot excels vs. struggles)
  Target: > 60% overall; iterate on worst-performing intents

CSAT (Customer Satisfaction):
  Post-conversation survey: "How satisfied are you? 1-5 stars"
  Response rate: ~25% (prompt at conversation end)
  Track: CSAT by intent, channel, language, model version

First Contact Resolution (FCR):
  % of issues resolved in one conversation without follow-up
  High FCR → efficient support

Escalation analysis:
  Why is the bot escalating? (intent distribution)
  If "technical" intent has 80% escalation rate → train on more tech KB articles
  Manual review of escalated conversations → identify gaps

Feedback loop:
  Human agent marks each escalation: "bot was close" / "bot was wrong" / "right to escalate"
  "Bot was close" cases → review conversation → add to KB or improve prompt
  Weekly: KB article managers review low-performance articles → update content

A/B testing:
  Test prompt variations (different system prompts)
  Test model versions (GPT-4o vs Claude 3.5)
  Test escalation thresholds (when to proactively escalate)
  Statistical significance: 1,000 conversations per variant minimum
```

---

## 6. Prompts and Safety

```
System prompt design:

[System]
You are a helpful customer support assistant for {company_name}.
Your goal is to resolve customer issues efficiently and empathetically.

GUIDELINES:
- Be concise. Customer support responses should be 2-4 sentences typically.
- Be empathetic. Acknowledge the customer's frustration before solving.
- Only use information from the provided knowledge base and customer data.
- If you don't know the answer, say so and offer to escalate.
- Never make promises about refunds, shipping dates, or policies not in your knowledge.
- Always offer next steps (what the customer can do or expect next).

ESCALATION TRIGGERS (respond with: ACTION: ESCALATE):
- Customer explicitly requests human agent
- Issue requires action beyond your tools
- Customer is very distressed or threatening
- Question requires policy judgment above your authority

PROHIBITED:
- Sharing other customers' data
- Making up information not in the provided context
- Discounting prices or giving credits without authorization
- Making guarantees the company hasn't authorized

TONE: Professional but warm. Match the customer's formality level.

Safety filters:
  Pre-LLM: intent classifier catches threats, crisis keywords
  Post-LLM: scan response for:
    - Accidental PII exposure (names/emails of other customers)
    - Policy violations (unauthorized discounts)
    - Commitments outside policy ("I guarantee delivery by X")
  Action: if flagged → substitute with safe fallback + escalate
```

---

## 7. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Bot-first vs. human-first | Bot-first (escalate as needed) | Human-first (opt-in bot) | 60% resolution savings; customers prefer instant response |
| Escalation threshold | Multiple signals (confidence + sentiment + turns) | Fixed confidence only | Sentiment prevents frustrated customers getting wrong answers |
| Action execution | Direct (process refunds) | Suggest (human approves) | For small amounts (<$100): direct is faster; builds trust |
| Context window | 3-turn summary + full context | Full history | Summary avoids "lost in the middle" for long conversations |
| Multilingual | Multilingual embedding model | Translate-first | Better semantic matching; lower latency |
| LLM choice | GPT-4o for Tier-1 bot; Claude Haiku for agent assist | Single model | Cost optimization; agent assist is lower stakes → smaller model |

---

## 8. Cost Analysis

```
1M conversations/day, 4 bot turns average:

LLM costs:
  4M bot turns × 2,200 tokens = 8.8B tokens/day
  Input: 8B × $5/1M = $40,000/day (GPT-4o)
  Output: 0.8B × $15/1M = $12,000/day
  LLM total: $52,000/day

Optimization:
  Route simple intents (50%) to Claude Haiku (10× cheaper):
    4M turns × 50% × 2,200 × avg $0.50/1M input + $1.25/1M output = ~$6,000/day
  Route complex intents (50%) to GPT-4o:
    4M turns × 50% × 2,200 × $5/1M + $15/1M output = ~$26,000/day
  Optimized LLM: $32,000/day (38% savings)

Infrastructure:
  Qdrant cluster + Elasticsearch: $500/day
  Redis (session + cache): $200/day
  Application servers: $300/day
  Total infra: $1,000/day

Total: ~$33,000/day = ~$1M/month

ROI comparison:
  Without AI bot: 1M conversations × 8 min average × $0.15/min agent cost = $1.2M/day
  With AI bot: 60% resolved by bot ($1M/month total cost)
              40% reach humans: 400K × 8 min × $0.15 = $480K/day
  Total with AI: $33K (AI) + $480K (humans) = $513K/day vs $1.2M/day
  Net savings: $687K/day = $250M/year
  ROI: extremely high; even at $1M/month AI cost, savings are 20×
```

---

## 9. Interview Discussion Points

**The escalation threshold is the most important product decision.** Too low → bot frustrates customers trying to help (answer quality suffers). Too high → expensive human agents handle issues bot could solve. The key insight: use sentiment as a primary signal, not just confidence. A confident but wrong answer to a frustrated customer is worse than an early escalation.

**Context handoff is where most bots fail.** The worst customer experience is a bot escalating to a human, and the human asks the customer to repeat everything. Full context handoff (conversation summary, customer data, escalation reason) eliminates this problem and is a significant differentiator.

**Tier-1 resolution rate is not the only metric.** A bot that achieves 80% resolution rate but leaves customers dissatisfied (low CSAT) is worse than one with 60% resolution and high satisfaction. Track resolution rate AND CSAT together. A "hard no" (bot confidently declining to help) might resolve an issue without customer satisfaction.

**Knowledge base quality is the primary lever for improvement.** When resolution rate is low for an intent, the fix is usually more/better KB content, not a better model. Hiring dedicated KB managers who continuously update articles based on failed conversations is often higher ROI than model improvements.

**Multilingual is harder than it looks.** Language detection fails on short messages. Cultural context affects communication style. The word "cancel" in German conversation might have different escalation signals than in English. Build language-specific test sets and measure CSAT separately by language.

---

## Escalation Logic Design

Escalation from bot to human agent is the highest-stakes transition in a customer support bot. A bad escalation (too slow, missing context) causes significant customer dissatisfaction.

**Escalation decision tree:**

```
User message received
        │
        ▼
┌───────────────────────┐   intent confidence < 0.6
│  Intent Classification│──────────────────────────► Escalate (uncertain intent)
└───────────┬───────────┘
            │ high confidence intent
            ▼
┌───────────────────────┐   intent in escalation_list
│  Policy Check         │──────────────────────────► Escalate (policy requires human)
│  (refunds >$500,      │   (e.g., "cancel account",
│   legal threats,      │    "I'm a lawyer", "I'll sue")
│   account compromise) │
└───────────┬───────────┘
            │ bot can handle
            ▼
┌───────────────────────┐   sentiment score < -0.7
│  Sentiment Detection  │──────────────────────────► Escalate (customer very angry)
└───────────┬───────────┘
            │ neutral/positive
            ▼
┌───────────────────────┐   resolution_attempts >= 3
│  Attempt Counter      │──────────────────────────► Escalate (bot failing to resolve)
└───────────┬───────────┘
            │
            ▼
      Bot continues
```

**Context handoff to human agent:**

```python
from dataclasses import dataclass
from datetime import datetime

@dataclass
class EscalationContext:
    conversation_id: str
    user_id: str
    account_tier: str
    escalation_reason: str
    sentiment_score: float
    resolution_attempts: int
    bot_summary: str          # LLM-generated summary of conversation
    identified_intent: str
    extracted_entities: dict  # order_id, product_sku, refund_amount, etc.
    conversation_history: list[dict]
    urgency_score: float      # 0-1: high urgency routes to senior agents

def generate_handoff_summary(
    conversation: list[dict],
    client,
    escalation_reason: str,
) -> str:
    """Generate a concise summary for the human agent to read in <30 seconds."""
    history_text = "\n".join([
        f"{msg['role'].upper()}: {msg['content']}"
        for msg in conversation[-10:]  # last 10 turns
    ])
    response = client.messages.create(
        model="claude-3-haiku-20240307",
        max_tokens=256,
        messages=[{
            "role": "user",
            "content": f"""Summarize this customer support conversation for a human agent.
Include: customer's core issue, what was already tried, reason for escalation.
Be concise (under 100 words).

Escalation reason: {escalation_reason}

Conversation:
{history_text}"""
        }]
    )
    return response.content[0].text
```

---

## Sentiment Detection

Real-time sentiment detection enables proactive escalation before the customer explicitly complains:

```python
from transformers import pipeline
import numpy as np

# Fine-tuned sentiment model: customer support domain (not general Twitter sentiment)
# Trained on support transcripts with human-labeled sentiment -1.0 to +1.0
sentiment_model = pipeline(
    "sentiment-analysis",
    model="./models/support-sentiment-v2",
    device=0,  # GPU
)

def compute_conversation_sentiment(
    messages: list[dict],
    decay_factor: float = 0.8,  # recent messages weighted more
) -> float:
    """Compute exponentially weighted sentiment across conversation."""
    user_messages = [m for m in messages if m["role"] == "user"]
    if not user_messages:
        return 0.0

    sentiments = []
    for msg in user_messages:
        result = sentiment_model(msg["content"][:512])[0]
        score = result["score"] if result["label"] == "POSITIVE" else -result["score"]
        sentiments.append(score)

    # Exponential decay: most recent message has highest weight
    weights = [decay_factor ** (len(sentiments) - 1 - i) for i in range(len(sentiments))]
    weighted_sum = sum(s * w for s, w in zip(sentiments, weights))
    return weighted_sum / sum(weights)

# Example: first message neutral (0.1), second frustrated (-0.4), third very angry (-0.9)
# Weighted sentiment: dominant by last message → escalation threshold -0.7 triggered
```

---

## CSAT Measurement Pipeline

Customer Satisfaction (CSAT) measurement for bot interactions requires careful survey design to avoid selection bias:

```python
import random
from enum import Enum

class CSATTrigger(Enum):
    RESOLUTION = "resolution"   # triggered when bot marks conversation resolved
    ESCALATION = "escalation"   # triggered when escalating (was bot helpful before escalation?)
    RANDOM = "random"           # 10% random sample for baseline measurement

def should_trigger_csat_survey(
    conversation_id: str,
    trigger: CSATTrigger,
    sampling_rate: float = 0.30,  # survey 30% of resolved conversations
) -> bool:
    """Deterministic sampling: same conversation always gets same decision."""
    if trigger == CSATTrigger.RANDOM:
        # Hash-based deterministic sampling: no database needed
        hash_val = int(hashlib.md5(conversation_id.encode()).hexdigest(), 16)
        return (hash_val % 100) < (sampling_rate * 100)
    elif trigger == CSATTrigger.ESCALATION:
        return True  # always survey escalations (high business value)
    elif trigger == CSATTrigger.RESOLUTION:
        hash_val = int(hashlib.md5(conversation_id.encode()).hexdigest(), 16)
        return (hash_val % 100) < (sampling_rate * 100)
    return False

def compute_csat_score(responses: list[dict]) -> dict:
    """CSAT = % of responses scoring 4 or 5 on a 5-point scale."""
    if not responses:
        return {"csat": None, "n": 0}
    satisfied = sum(1 for r in responses if r["score"] >= 4)
    return {
        "csat": satisfied / len(responses),
        "n": len(responses),
        "score_distribution": {i: sum(1 for r in responses if r["score"] == i) for i in range(1, 6)},
    }
```

---

## Knowledge Base Freshness Pipeline

The KB freshness pipeline ensures bot responses are based on current product information:

```python
from datetime import datetime, timedelta
import hashlib

class KBFreshnessMonitor:
    def __init__(self, db, vector_db, stale_threshold_days: int = 7):
        self.db = db
        self.vector_db = vector_db
        self.stale_threshold = timedelta(days=stale_threshold_days)

    def check_freshness(self) -> dict:
        """Return articles that are stale or have changed since last indexing."""
        articles = self.db.query("SELECT id, content_hash, last_modified, last_indexed FROM kb_articles")
        stale, changed = [], []
        now = datetime.utcnow()
        for article in articles:
            if now - article["last_indexed"] > self.stale_threshold:
                stale.append(article["id"])
            current_hash = self._compute_hash(article["id"])
            if current_hash != article["content_hash"]:
                changed.append(article["id"])
        return {"stale_count": len(stale), "changed_count": len(changed),
                "stale_ids": stale, "changed_ids": changed}

    def reindex_changed_articles(self, article_ids: list[str]) -> int:
        reindexed = 0
        for article_id in article_ids:
            content = self.db.get_article_content(article_id)
            embedding = embed(content)
            self.vector_db.upsert(article_id, embedding, metadata={
                "article_id": article_id,
                "indexed_at": datetime.utcnow().isoformat(),
            })
            self.db.update("kb_articles", article_id, {
                "content_hash": hashlib.md5(content.encode()).hexdigest(),
                "last_indexed": datetime.utcnow(),
            })
            reindexed += 1
        return reindexed
```

---

## Multi-Language Support Architecture

```
User sends message in any of 40+ supported languages
        │
        ▼
Language detection (fasttext langdetect, 3ms, 176 languages)
        │
        ├─ Confidence < 0.8 → ask user: "Could you clarify your language preference?"
        │
        ▼
Route to language-specific KB index
(separate vector index per language: EN, ES, FR, DE, JA, ZH, etc.)
        │
        ▼
Retrieve KB articles in detected language
(translated KB articles maintained by localization team)
        │
        ▼
Generate response in detected language
(system prompt: "Always respond in {language_code}")
        │
        ▼
Quality check: does response language match detected language?
(simple regex: detect CJK characters for ZH/JA, Latin for EN/ES/FR)
```

**Language-specific CSAT monitoring:**

```python
def monitor_csat_by_language(csat_responses: list[dict]) -> dict:
    """Group CSAT scores by language to detect language-specific quality gaps."""
    by_language: dict[str, list[float]] = {}
    for response in csat_responses:
        lang = response.get("detected_language", "unknown")
        by_language.setdefault(lang, []).append(response["score"])
    return {
        lang: {
            "csat": sum(1 for s in scores if s >= 4) / len(scores),
            "n": len(scores),
            "avg_score": sum(scores) / len(scores),
        }
        for lang, scores in by_language.items()
        if len(scores) >= 20  # minimum sample size for reliable CSAT
    }
# Alert: if CSAT for any language drops >10% below English CSAT, flag for KB review
```

---

## Failure Scenarios and Recovery

**Failure 1 — Intent Model Confidence Collapse During Product Launch Surge**

During a major product launch, 40% of incoming queries were about the new product (which didn't exist in the training data). The intent classifier returned confidence < 0.6 for 40% of all messages (vs normal 5%), triggering escalation for all of them. Human agent queue depth spiked from 8 conversations to 340 in 30 minutes, causing 45-minute wait times.

**Detection:** Escalation rate alert (threshold: >20% of conversations) fired within 8 minutes.

**Recovery:** (1) Immediate: raised the escalation confidence threshold from 0.6 to 0.4 for the next 4 hours (accept lower-confidence intent matches to reduce escalation rate). (2) Added a "new product" catch-all intent with a pre-written FAQ response. (3) Long-term: added a pre-launch KB update process — product FAQ articles indexed 48 hours before launch so the intent model has training signal.

**Failure 2 — Context Handoff Dropping Critical Information During Agent Transfer**

The handoff context was constructed from the last 5 conversation turns. A customer had mentioned their order number in turn 2 (13 turns ago) and their preferred resolution (store credit) in turn 8 (7 turns ago). The human agent received a summary that included neither, asking the customer to repeat their order number — triggering a 1-star review.

**Recovery:** Updated the handoff summary prompt to explicitly extract structured entities from the full conversation, not just the last 5 turns. The LLM-generated summary now includes: `order_id`, `product_sku`, `requested_resolution`, `previous_resolutions_offered` as structured fields alongside the narrative summary.

---

## Additional Interview Questions

**How do you design escalation routing to ensure high-priority customers reach senior agents faster?** Implement priority queue routing: assign each conversation a priority score at escalation time based on: customer lifetime value (CLV) tier, account age, churn risk score, escalation urgency (self-harm/legal/fraud routes immediately to senior agents), and conversation sentiment. The agent assignment system pops from the highest-priority queue first. Senior agents are pooled separately and receive only priority-flagged conversations. Track "time to first human response" segmented by priority tier as a key SLA metric.

**What is the difference between resolution rate and containment rate in a customer support bot, and which matters more?** Resolution rate: the percentage of conversations where the customer's issue is resolved without escalation. Containment rate: the percentage of conversations that never reach a human agent (includes conversations where the bot said "I can't help" and the customer left without escalating). Containment rate is always higher than resolution rate. Resolution rate matters more for business impact — a bot that contains 80% of conversations but resolves only 40% (the other 40% leave unsatisfied) is worse than a bot that contains 60% but resolves 58% of total conversations. Track both and measure CSAT separately for contained vs escalated conversations.

**How do you handle cases where the bot gives wrong information (hallucination) that causes customer harm, such as incorrect refund policy information?** Mitigation architecture: (1) all factual claims must come from the RAG KB, not the model's training data — use strict grounding with citation; (2) output validation against the KB: if the generated response contains a policy statement (refund, warranty, cancellation), a secondary check queries the KB for the policy and verifies the response matches; (3) response templating for critical policies (refund amounts, warranty terms) — use string interpolation from the KB into a template rather than free-form generation; (4) post-incident: implement "dead reckoning" — when a customer calls to dispute a policy the bot stated, log the discrepancy and audit the KB for outdated entries.

**How do you measure and improve the bot's performance on rare intents (long-tail queries) that don't appear frequently enough for reliable CSAT measurement?** For rare intents (N < 20 responses per week), CSAT is statistically unreliable. Instead: (1) use human evaluation — route all rare intent conversations to a weekly expert review pool; reviewers label resolution quality (0-100) using a rubric; (2) track resolution attempt count — if rare intent conversations require >2 attempts before resolution or escalation, the bot is struggling; (3) use automated LLM-as-judge on 100% of rare intent conversations (feasible since volume is low) with a rubric specific to each intent type; (4) aggregate all rare intents into a "long-tail" bucket and set an overall long-tail resolution rate target (e.g., 65% vs 80% for common intents).

**How would you architect a customer support bot to handle a sudden 10x traffic spike (e.g., a product recall or major outage)?** Design for elasticity at every layer: (1) intent classifier and embedding models on horizontally scalable container replicas (Kubernetes HPA, scale on queue depth metric, not CPU); (2) LLM API calls use a request queue with configurable throughput limits per tier — during spikes, free-tier customers see longer waits rather than errors; (3) pre-warm a static "outage mode" response template that bypasses RAG and LLM generation (serves a pre-written status page response in <50ms, no GPU needed); (4) circuit breaker on KB retrieval: if retrieval latency exceeds 2s, fall back to a cached "top 20 most common issues" response without live retrieval; (5) activate proactive communication (push email/SMS to affected users) to reduce inbound volume by 30-40% during known outages.
