# AI Applications

## 1. Concept Overview

LLMs are being deployed across virtually every industry, transforming workflows that previously required specialized human expertise. Understanding domain-specific applications is valuable for system design interviews — you need to know not just the technical architecture but the specific requirements, constraints, and regulatory concerns unique to each domain.

Each domain has distinct requirements: healthcare demands accuracy and regulatory compliance above all; legal requires precision and citation; finance needs recency and verifiability; customer support needs speed and escalation paths; education requires personalization and appropriate challenge calibration.

---

## Intuition

> **One-line analogy**: Applying LLMs to industry domains is like giving each specialist (doctor, lawyer, teacher) a brilliant assistant who has read every textbook but needs domain-specific guardrails and verification.

**Mental model**: General LLMs have broad knowledge but lack domain-specific judgment, regulatory awareness, and the ability to say "I don't know" reliably. Effective domain applications combine LLM capabilities with domain expertise: specialized fine-tuning or prompting for the domain vocabulary, RAG over domain-specific knowledge bases, strict output validation, human-in-the-loop for high-stakes decisions, and regulatory compliance guardrails.

**Why it matters**: The highest-value LLM applications are in high-expertise domains where scaling human expertise is expensive — healthcare (physician time), legal (attorney time), finance (analyst time). But the risks are also highest in these domains, requiring careful design.

**Key insight**: Domain applications fail not from lack of LLM capability but from insufficient domain adaptation — generic models hallucinate domain-specific facts, miss regulatory requirements, and lack calibrated uncertainty in high-stakes contexts.

---

## 2. Domain Deep Dives

### 2.1 Healthcare

**Key use cases:**
- Clinical documentation: auto-generate SOAP notes from doctor-patient conversations
- Diagnostic support: differential diagnosis from symptoms (not replacement for doctor)
- Medical literature: summarize relevant studies for treatment decisions
- Drug interaction checking: flag dangerous combinations
- Patient triage: chat-based symptom assessment for routing

**Notable systems:**
```
Med-PaLM 2 (Google):
  Trained on medical exam data, research papers, clinical notes
  USMLE: 85%+ (passing score ~60%; most doctors: 80-90%)
  Used in: Google Cloud Healthcare API, hospital pilots

GPT-4 (deployed by various hospitals):
  Discharge summary generation: 90%+ reduction in documentation time
  Clinical trial eligibility matching: automated patient screening
  Epic integration: ambient AI documentation

Nuance DAX (Microsoft/Nuance):
  Clinical documentation AI; widely deployed
  Microphone in exam room → structured clinical note
  Used in 30%+ of US health systems
```

**Regulatory considerations:**
```
FDA clearance: Any "clinical decision support" software → FDA 510(k) or De Novo
  Risk-based: low risk (informational) → no clearance needed
               high risk (treatment recommendation) → clearance required

HIPAA:
  PHI in prompts: requires BAA with LLM provider
  Audit trails: all AI interactions logged
  Access control: only treating clinicians

Liability: AI cannot be liable; hospital + physician remain responsible
  Common approach: "AI-assisted" not "AI-decided"
  Human review required for clinical decisions
```

**System design pattern:**
```
Patient Encounter
     |
     v
[Audio transcription] (Whisper, Nuance)
     |
     v
[PHI detection + redaction]
     |
     v
[Clinical LLM] (Med-PaLM, fine-tuned GPT-4)
  System: "You are a clinical documentation assistant..."
  Context: specialty, patient demographics, prior notes
     |
     v
[Draft note generation]
     |
     v
[Physician review + correction] (mandatory human-in-the-loop)
     |
     v
[EHR submission]
```

### 2.2 Legal

**Key use cases:**
- Contract analysis: extract key clauses, obligations, risks
- Document review: e-discovery, flag relevant documents from millions
- Legal research: find relevant case law, statutes, precedents
- Contract drafting: generate first drafts from templates + parameters
- Compliance monitoring: check if documents comply with regulations

**Notable systems:**
```
Harvey AI:
  Legal-specialized LLM trained on case law, contracts, legal texts
  Used by major law firms: Allen & Overy, PwC Legal
  Use cases: document review, research, drafting
  Privacy: on-premise deployment options for client confidentiality

Casetext CoCounsel (acquired by Thomson Reuters):
  Legal research over Westlaw database
  RAG over curated legal databases
  Bar passage: near-human performance on state bar exams

Thomson Reuters AI Assistant:
  Integrated with Westlaw and Practical Law
  Legal research + drafting + analysis
```

**Regulatory considerations:**
```
Attorney-client privilege:
  Communications between attorney and client are confidential
  LLM provider cannot be a third party that breaks privilege
  Solution: on-premise deployment or BAA equivalent

Unauthorized Practice of Law (UPL):
  AI cannot practice law; attorneys use AI as a tool
  LLM output must be reviewed by licensed attorney
  Direct-to-consumer legal AI is legally risky

Jurisdiction-specific law:
  Laws differ dramatically by jurisdiction
  A contract valid in Delaware may be unenforceable in California
  Legal AI must specify jurisdiction and limitations

Hallucination risk in legal context:
  Hallucinated citations are extremely dangerous
  "Smith v. Jones (2019)" that doesn't exist → sanctions
  RAG over verified legal databases is essential
```

### 2.3 Finance

**Key use cases:**
- Financial document analysis: earnings reports, SEC filings, analyst reports
- Trading signal generation: sentiment from news, earnings call transcripts
- Risk assessment: credit risk, fraud detection narratives
- Regulatory reporting: generate XBRL, compliance reports
- Customer financial planning: personalized advice (regulated)

**Notable systems:**
```
BloombergGPT:
  50B params trained on 700B financial tokens + 300B general
  Outperforms general models on financial tasks (FiQA, ConvFinQA)
  Not deployed as general product; internal Bloomberg use

FinGPT (open source):
  8B model fine-tuned on financial data
  Focused on: news analysis, sentiment, forecasting

JPMorgan Chase:
  COiN (Contract Intelligence): LLM for loan agreement analysis
  DocLLM: document layout-aware LLM
  IndexGPT: ETF construction AI

Morgan Stanley:
  AI @ Morgan Stanley assistant: RAG over 100K+ research documents
  Deployed to financial advisors; GPT-4 based
```

**Regulatory considerations:**
```
MiFID II / Dodd-Frank: Financial advice requires licensing
  AI cannot give personalized investment advice without human oversight
  "Investment suggestions" vs. "educational information" line is thin

SEC Regulations:
  Material non-public information (MNPI): LLM cannot use MNPI
  Record-keeping: all AI-generated advice must be logged
  Explainability: "why did the AI recommend this?" questions arise

Model risk:
  Basel/SR 11-7: banks must validate AI models
  Backtesting, stress testing required
  Ongoing monitoring for model drift

Anti-money laundering (AML):
  LLM for suspicious transaction narrative generation
  Reduces analyst workload; must maintain audit trail
```

### 2.4 Education

**Key use cases:**
- Personalized tutoring: adapt explanations to student's level
- Automated grading: essays, code assignments, short answers
- Content generation: practice problems, quizzes, flashcards
- Student writing assistance (with controversy)
- Language learning: conversation practice, grammar feedback

**Notable systems:**
```
Khan Academy Khanmigo:
  GPT-4 based; Socratic method tutor (asks questions, doesn't give answers)
  Subject coverage: math, science, programming, history
  System prompt: "Never give the answer directly. Guide the student to discover it."

Duolingo (AI-powered):
  AI conversation practice with simulated native speakers
  Personalized review: Duolingo algorithm identifies weak points
  "Roleplay" scenarios for real-world language use

Carnegie Learning / MATHia:
  AI math tutor with 20+ years of student interaction data
  Adaptive: adjusts difficulty based on mastery evidence
  Measured outcomes: significantly better than traditional instruction

GitHub Education:
  Copilot free for students: learning to code with AI assistance
  Debate: does it teach coding or bypass learning?
```

**System design for personalized tutoring:**
```
Student: "I don't understand why my sort isn't working"
     |
     v
[Student Model] → what topics has this student mastered? common misconceptions?
     |
     v
[LLM System Prompt]
  "Student is in 8th grade, mastered variables, loops, not yet conditions.
   Use the Socratic method. Never give the answer directly.
   Ask guiding questions. Current topic: lists and sorting."
     |
     v
[Tutor LLM Response]
  "Let's look at your sort together! What do you think the sort() function needs
   to know to order your list correctly?"
     |
     v
[Update Student Model] based on conversation
```

### 2.5 Customer Support

**Key use cases:**
- First-line chatbot: handle tier-1 inquiries without human agent
- Agent assist: suggest responses, relevant knowledge, escalation triggers
- Ticket routing: classify and route to correct team
- Knowledge base Q&A: answer from product documentation
- Sentiment analysis + escalation: detect frustrated customers, escalate

**Notable systems:**
```
Intercom (Fin AI Agent):
  RAG over customer's knowledge base
  Handles 40-60% of support inquiries autonomously
  Escalates to humans for complex/sensitive issues

Salesforce Einstein (Service Cloud):
  AI-suggested replies for agents
  Case classification and routing
  Next best action recommendations

Zendesk AI:
  Autonomous resolution for simple tickets
  Article recommendations for agents
  CSAT prediction
```

**Architecture:**
```
Customer Message
     |
     v
[Intent Classifier]
  category: billing | technical | general | escalation_needed
     |
     +-- High urgency / angry customer → route to human
     |
     +-- Complex technical → RAG over technical docs
     |
     +-- Billing → RAG over customer account + billing docs
     |
     v
[RAG + LLM Response]
  Retrieve: relevant help articles, customer history, similar resolved tickets
  Generate: helpful response with specific answer + next steps
     |
     v
[Guardrail check]
  Safety | factuality | tone | completeness
     |
     v
[Escalation check]
  If LLM not confident → offer human agent
  If customer explicitly requests human → immediate escalation
     |
     v
Customer Response + Follow-up survey
```

### 2.6 Creative & Enterprise

**Creative:**
```
Content generation (marketing, copywriting):
  Jasper, Copy.ai: marketing copy at scale
  Adobe Firefly: AI creative tools in Creative Cloud
  Canva AI: template + text + image generation

Code documentation:
  Auto-generate README, API docs, inline comments
  Speeds up developer documentation 5-10×

Meeting summarization:
  Otter.ai, Fireflies.ai: real-time transcription + summary
  Key decisions, action items, follow-up items
```

**Enterprise:**
```
Document processing:
  Invoice extraction (line items, amounts, vendors)
  Contract review and risk flagging
  HR document analysis (resumes, performance reviews)

Data analysis:
  Text-to-SQL: ask questions in English, get SQL + results
  Report generation: given data → generate narrative analysis
  Anomaly explanation: "Why is revenue down 15% this month?"

Email and communication:
  Email drafting: user provides bullet points → full email
  Meeting scheduling: natural language booking
  Communication style adaptation: formal/informal
```

---

## 3. Architecture Diagrams

### Multi-Domain LLM Platform
```
Domain-Specific LLM Applications

Healthcare    Legal       Finance     Education    Customer Support
    |             |           |            |              |
    v             v           v            v              v
[Domain Fine-Tuned Models or Domain RAG Indices]
    |
    v
[Common Infrastructure Layer]
  Gateway | Auth | Rate Limiting | Logging | Guardrails
    |
    v
[Model Tier]
  GPT-4o / Claude 3.5 / Gemini / Self-hosted
    |
    v
[Data Layer]
  Vector DBs | Knowledge Bases | User Data | Audit Logs
```

---

## 4. ROI Frameworks for LLM Adoption

### Estimating ROI

```
Direct savings:
  Time savings: (hours/task × tasks/day × FTE rate) saved
  Error reduction: (error rate × error cost × volume) saved
  Scale: handle 10× volume with same headcount

Example: Customer support chatbot
  Before: 100 agents × $50K/year = $5M/year
           60% of tickets simple (tier-1)
  After: AI handles 80% of tier-1 tickets
  Savings: 100 agents × 60% tier-1 × 80% automated = 48 agents freed
  ROI: 48 × $50K = $2.4M savings/year - $500K AI cost = $1.9M net benefit

Revenue uplift:
  Faster response → higher customer satisfaction → lower churn
  Personalization → higher conversion rate
  Availability → capture queries that would have gone unanswered
```

---

## 5. Interview Questions with Answers

**Q: How would you design a medical documentation AI that is HIPAA-compliant?**
A: Key components: (1) Consent layer — patient consent for AI processing; (2) Audio transcription — ambient microphone → Whisper/Nuance; (3) PHI detection before any external API call — NER to identify names, DOB, SSN, diagnoses → redact or use on-premise model; (4) Clinical LLM — on-premise fine-tuned model or cloud with BAA (Business Associate Agreement); (5) Mandatory physician review — AI generates draft, physician reviews/edits before any clinical use; (6) Audit logging — every AI interaction logged with timestamp, user, changes made; (7) EHR integration — final approved notes sent to Epic/Cerner. The "human-in-the-loop" for review is non-negotiable.

**Q: What are the unique challenges of LLMs in legal applications?**
A: (1) Hallucinated citations — the most dangerous failure mode: a model citing a non-existent case can result in sanctions; RAG over verified legal databases is essential; (2) Jurisdiction specificity — law differs dramatically by location; must always specify and validate jurisdiction; (3) Privilege — communications must remain confidential; on-premise or BAA required; (4) Unauthorized practice — AI cannot practice law; all outputs reviewed by licensed attorney; (5) Accuracy requirements — legal documents require extreme precision; post-generation verification against authoritative sources.

**Q: How would you design a customer support bot that handles both automated responses and escalation?**
A: Multi-tier architecture: (1) Intent classification — route to human immediately for: high anger sentiment, explicit escalation requests, keywords like "legal action" or "fraud"; (2) RAG pipeline — retrieve from knowledge base, customer history, similar resolved tickets; (3) Confidence scoring — if LLM confidence < threshold, offer human escalation; (4) Guardrails — check for factually correct, appropriately toned responses; (5) Escalation handoff — when escalating, pass full conversation context to human agent so customer doesn't repeat; (6) Feedback loop — track resolution rate and CSAT per intent type to improve routing.

---

## 6. Best Practices

1. **Domain fine-tuning is worth it for regulated industries** — generic models miss domain-specific terminology and nuance.
2. **RAG over verified sources** — for medical, legal, financial — never rely on parametric knowledge alone.
3. **Human-in-the-loop for high-stakes decisions** — AI assists, humans decide.
4. **Measure domain-specific metrics** — not just MMLU; clinical accuracy, contract extraction precision, resolution rate.
5. **Plan for regulatory evolution** — AI regulations are actively changing; build with compliance hooks from day one.
6. **Start narrow** — pick the single most impactful, lowest-risk use case first; prove ROI; then expand.

---

## 7. Case Study: AI-Powered Legal Document Review

**Problem:** Law firm reviews 1000 contracts/month for M&A due diligence. Each contract ~50 pages; 5 attorneys × 8 hours each = 40 attorney-hours per contract. At $500/hr = $20K per contract, $20M/year.

**AI Solution:**
```
Architecture:
  1. Contract parsing: PDF → structured text (preserve formatting)
  2. Clause extraction: identify specific clause types (indemnification, IP, termination)
  3. Risk flagging: compare clause language against risk taxonomy
  4. Issue summary: plain-English explanation of each flagged issue

Pipeline:
  Contract PDF
       |
       v
  [DocAI: layout-preserving extraction]
       |
       v
  [Clause segmentation: fine-tuned LegalBERT]
       |
       v
  [Risk analysis: GPT-4o with legal fine-tuning]
    System: "You are a senior M&A attorney. Identify risks in each clause.
             Rate severity 1-5. Cite specific language."
       |
       v
  [Output: structured report with highlights + attorney review interface]
       |
       v
  [Attorney review: confirm/reject flags, add notes]

Performance:
  AI first-pass review: 25 minutes per contract (vs. 40 hours)
  Attorney review of AI output: 3 hours per contract
  Total: 3.25 hours (vs. 40 hours)
  Cost: $1,625 (vs. $20,000)
  Savings: 91.8% cost reduction

Quality metrics:
  Clause identification recall: 94%
  Risk flag precision: 87% (13% false positives reviewed by attorneys)
  Critical issue capture rate: 99% (most important metric)
```
