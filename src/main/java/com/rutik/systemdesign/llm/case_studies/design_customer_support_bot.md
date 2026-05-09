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
